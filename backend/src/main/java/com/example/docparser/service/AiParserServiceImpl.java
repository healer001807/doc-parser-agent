package com.example.docparser.service;

import com.example.docparser.model.dto.FieldDefinition;
import com.example.docparser.model.dto.InterfaceInfo;
import com.example.docparser.model.dto.ParseResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiParserServiceImpl implements AiParserService {

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;
    private String systemPromptTemplate;

    @Value("classpath:prompts/system-prompt.txt")
    private Resource systemPromptResource;

    public AiParserServiceImpl(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name:deepseek-v4-pro}") String modelName,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(16384)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @PostConstruct
    public void init() throws IOException {
        this.systemPromptTemplate = StreamUtils.copyToString(
                systemPromptResource.getInputStream(), StandardCharsets.UTF_8);
    }

    @Override
    public ParseResult parseDocument(String documentText, String fileName) {
        return parseWithAI(documentText, fileName, null);
    }

    @Override
    public ParseResult parseDocumentWithHint(String documentText, String fileName, String userHint) {
        return parseWithAI(documentText, fileName, userHint);
    }

    private ParseResult parseWithAI(String documentText, String fileName, String userHint) {
        try {
            String fileType = inferFileType(fileName);
            String documentType = inferDocumentType(documentText);

            String systemPrompt = buildSystemPrompt(fileType, documentType, userHint);

            PromptTemplate promptTemplate = PromptTemplate.from(systemPrompt);
            Map<String, Object> variables = new HashMap<>();
            variables.put("documentContent", truncateText(documentText, 15000));
            Prompt prompt = promptTemplate.apply(variables);

            log.info("正在调用AI模型解析文档: {}, 文档类型: {}, 长度: {}字符",
                    fileName, documentType, documentText.length());

            String response = chatModel.generate(prompt.text());

            log.info("AI解析完成，响应长度: {}字符", response.length());

            return parseAiResponse(response, documentText);

        } catch (Exception e) {
            log.error("AI解析失败: {}", e.getMessage(), e);
            return fallbackParse(documentText, fileName);
        }
    }

    private String buildSystemPrompt(String fileType, String documentType, String userHint) {
        String hintSection = (userHint != null && !userHint.isBlank())
                ? "## 用户补充说明\n" + userHint + "\n\n"
                : "";
        String resolved = systemPromptTemplate
                .replace("{{fileType}}", fileType)
                .replace("{{documentType}}", documentType)
                .replace("{{userHint}}", hintSection);
        return resolved + "\n\n## 文档内容\n{{documentContent}}";
    }

    private String inferFileType(String fileName) {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "Word";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
        if (lower.endsWith(".txt")) return "TXT";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "Excel";
        if (lower.endsWith(".swagger") || lower.endsWith(".yaml") || lower.endsWith(".yml")) return "Swagger";
        return "unknown";
    }

    private String inferDocumentType(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("swagger") || lower.contains("openapi")) return "Swagger/OpenAPI";
        if (lower.contains("postman")) return "Postman";
        if (text.contains("请求参数") || text.contains("请求报文") || text.contains("响应报文")) return "中文接口文档";
        if (text.contains("request") && text.contains("response")) return "API文档";
        return "通用文档";
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n\n...（文档过长，已截断，总计" + text.length() + "字符）";
    }

    private ParseResult parseAiResponse(String aiResponse, String originalText) {
        try {
            String jsonStr = extractJsonFromResponse(aiResponse);

            if (jsonStr != null) {
                // 尝试直接解析
                try {
                    return tryParseJson(jsonStr, originalText);
                } catch (JsonProcessingException e) {
                    log.warn("JSON格式有误，尝试修复: {}", e.getMessage());
                }

                // 尝试修复后解析
                String repaired = repairTruncatedJson(jsonStr);
                if (repaired != null) {
                    try {
                        ParseResult result = tryParseJson(repaired, originalText);
                        log.info("JSON修复成功");
                        return result;
                    } catch (JsonProcessingException e2) {
                        log.warn("修复后仍无法解析: {}", e2.getMessage());
                    }
                }
            }

            // 最终尝试：暴力取第一个{到最后一个}
            String fallback = extractJsonFallback(aiResponse);
            if (fallback != null) {
                try {
                    ParseResult result = tryParseJson(fallback, originalText);
                    log.info("暴力提取JSON成功");
                    return result;
                } catch (JsonProcessingException e) {
                    log.warn("暴力提取JSON后解析失败: {}", e.getMessage());
                    String repaired2 = repairTruncatedJson(fallback);
                    if (repaired2 != null) {
                        try {
                            ParseResult result = tryParseJson(repaired2, originalText);
                            log.info("暴力提取+修复JSON成功");
                            return result;
                        } catch (JsonProcessingException e2) {
                            log.warn("暴力提取+修复后仍然失败: {}", e2.getMessage());
                        }
                    }
                }
            }

            log.warn("无法从AI响应中提取有效JSON，返回部分结果");
            return buildPartialResult(aiResponse, originalText);

        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage());
            return buildPartialResult(aiResponse, originalText);
        }
    }

    private ParseResult tryParseJson(String json, String originalText) throws JsonProcessingException {
        ParseResult result = objectMapper.readValue(json, ParseResult.class);
        if (result != null) {
            result.setRawParsedText(originalText);
        }
        return result;
    }

    private String repairTruncatedJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        raw = raw.trim();
        raw = raw.replaceAll("[.…]{2,}$", "").trim();

        Deque<Character> stack = new ArrayDeque<>();
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int lastValidEnd = 0;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                result.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                result.append(c);
                if (!inString) {
                    lastValidEnd = result.length();
                }
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    stack.push('}');
                    result.append(c);
                } else if (c == '}') {
                    if (!stack.isEmpty() && stack.peek() == '}') {
                        stack.pop();
                        result.append(c);
                        lastValidEnd = result.length();
                    } else {
                        break;
                    }
                } else if (c == '[') {
                    stack.push(']');
                    result.append(c);
                } else if (c == ']') {
                    if (!stack.isEmpty() && stack.peek() == ']') {
                        stack.pop();
                        result.append(c);
                        lastValidEnd = result.length();
                    } else {
                        break;
                    }
                } else if (c == ',' || c == ':') {
                    result.append(c);
                    lastValidEnd = result.length();
                } else {
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }

        if (inString) {
            String s = result.substring(0, lastValidEnd);
            result = new StringBuilder(s);
        }

        String repaired = result.toString().replaceAll("[,\\s]+$", "");

        while (!stack.isEmpty()) {
            repaired += stack.pop();
        }

        return repaired;
    }

    private String extractJsonFromResponse(String response) {
        if (response == null) return null;

        String trimmed = response.trim();

        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.MULTILINE);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(trimmed);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1).trim();
        }

        int braceStart = trimmed.indexOf('{');
        if (braceStart < 0) {
            log.warn("AI响应中未找到{，前200字符: {}", truncateText(trimmed, 200));
            return null;
        }

        // 栈匹配法：从第一个{开始找匹配的}
        Deque<Character> stack = new ArrayDeque<>();
        boolean inStr = false;
        boolean esc = false;
        int braceEnd = -1;
        for (int i = braceStart; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') { stack.push('}'); }
            else if (c == '[') { stack.push(']'); }
            else if (c == '}') {
                if (stack.isEmpty()) break;
                if (stack.peek() == '}') { stack.pop(); if (stack.isEmpty()) { braceEnd = i; break; } }
                else break;
            }
            else if (c == ']') {
                if (stack.isEmpty()) break;
                if (stack.peek() == ']') { stack.pop(); if (stack.isEmpty()) { braceEnd = i; break; } }
                else break;
            }
        }

        if (braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }

        // 栈匹配失败，日志前200字符帮助排查
        log.warn("栈匹配JSON失败，响应前200字符: {}", truncateText(trimmed, 200));
        return null;
    }

    private String extractJsonFallback(String response) {
        if (response == null) return null;
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }

    private ParseResult buildPartialResult(String aiResponse, String originalText) {
        return ParseResult.builder()
                .interfaceInfo(InterfaceInfo.builder()
                        .apiNameCn("（AI解析失败，请手动填写）")
                        .apiNameEn("（AI解析失败，请手动填写）")
                        .build())
                .requestParams(new ArrayList<>())
                .responseParams(new ArrayList<>())
                .rawParsedText(originalText)
                .build();
    }

    private ParseResult fallbackParse(String documentText, String fileName) {
        log.info("使用降级方案解析文档: {}", fileName);

        List<FieldDefinition> requestFields = extractFieldsByRegex(documentText, false);
        List<FieldDefinition> responseFields = extractFieldsByRegex(documentText, true);

        return ParseResult.builder()
                .interfaceInfo(InterfaceInfo.builder()
                        .apiNameCn("（请手动填写）")
                        .apiNameEn("（请手动填写）")
                        .dataImportMethod("无")
                        .apiCallFrequency("无")
                        .callSystemName("无")
                        .build())
                .requestParams(requestFields)
                .responseParams(responseFields)
                .rawParsedText(documentText)
                .build();
    }

    private List<FieldDefinition> extractFieldsByRegex(String text, boolean isResponse) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (text == null || text.isBlank()) return fields;

        Pattern fieldPattern = Pattern.compile(
                "(?:字段|参数|field|param)\\s*[：:]?\\s*([\\w.]+)\\s*" +
                "(?:描述|说明|desc|description)\\s*[：:]?\\s*([^\\n,，。]*)" +
                "(?:类型|type)\\s*[：:]?\\s*(\\w+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = fieldPattern.matcher(text);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (seen.add(name)) {
                String desc = matcher.group(2).trim();
                String type = matcher.group(3).trim();
                fields.add(createDefaultField(name, desc, type, "无", "无"));
            }
        }

        return fields;
    }

    private FieldDefinition createDefaultField(String nameEn, String nameCn, String type, String length, String required) {
        return FieldDefinition.builder()
                .fieldNameEn(nameEn)
                .fieldNameCn(nameCn)
                .fieldType(type)
                .fieldLength(length)
                .required(required)
                .remark("无")
                .fieldNameEn2(nameEn)
                .externalFieldName("无")
                .children(new ArrayList<>())
                .build();
    }
}
