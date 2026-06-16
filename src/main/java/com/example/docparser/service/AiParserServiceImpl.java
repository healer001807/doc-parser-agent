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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于LangChain4j的AI智能解析实现
 * 使用大语言模型理解第三方接口文档，提取结构化信息
 */
@Slf4j
@Service
public class AiParserServiceImpl implements AiParserService {

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o}")
    private String modelName;

    public AiParserServiceImpl(
            @Value("${langchain4j.open-ai.chat-model.api-key:sk-your-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // 构建OpenAI Chat Model (LangChain4j)
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o")
                .temperature(0.1)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Override
    public ParseResult parseDocument(String documentText, String fileName) {
        return parseWithAI(documentText, fileName, null);
    }

    @Override
    public ParseResult parseDocumentWithHint(String documentText, String fileName, String userHint) {
        return parseWithAI(documentText, fileName, userHint);
    }

    /**
     * 核心AI解析方法
     */
    private ParseResult parseWithAI(String documentText, String fileName, String userHint) {
        try {
            String fileType = inferFileType(fileName);
            String documentType = inferDocumentType(documentText);

            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(fileType, documentType, userHint);

            // 使用LangChain4j PromptTemplate构建提示
            PromptTemplate promptTemplate = PromptTemplate.from(systemPrompt);
            Map<String, Object> variables = new HashMap<>();
            variables.put("documentContent", truncateText(documentText, 30000));
            Prompt prompt = promptTemplate.apply(variables);

            log.info("正在调用AI模型解析文档: {}, 文档类型: {}, 长度: {}字符",
                    fileName, documentType, documentText.length());

            // 调用AI模型
            String response = chatModel.generate(prompt.text());

            log.info("AI解析完成，响应长度: {}字符", response.length());

            // 解析AI返回的JSON
            return parseAiResponse(response, documentText);

        } catch (Exception e) {
            log.error("AI解析失败: {}", e.getMessage(), e);
            // 返回降级解析结果
            return fallbackParse(documentText, fileName);
        }
    }

    /**
     * 构建系统提示词（核心Prompt Engineering）
     */
    private String buildSystemPrompt(String fileType, String documentType, String userHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的第三方API接口文档解析专家。");
        sb.append("请仔细阅读以下第三方接口文档内容，提取结构化信息，并以严格的JSON格式返回。\n\n");

        sb.append("## 文档信息\n");
        sb.append("- 文件类型: ").append(fileType).append("\n");
        sb.append("- 文档类型推测: ").append(documentType).append("\n\n");

        if (userHint != null && !userHint.isBlank()) {
            sb.append("## 用户补充说明\n").append(userHint).append("\n\n");
        }

        sb.append("## 提取要求\n");
        sb.append("请从文档中提取以下三部分信息：\n\n");

        sb.append("### 第一部分：接口基本信息\n");
        sb.append("- internalApiName: 内部接口名（根据业务逻辑推断，如：订单查询接口）\n");
        sb.append("- externalApiName: 外部接口名（文档中提供的接口名称）\n");
        sb.append("- companyCode: 第三方公司简称（从文档上下文推断）\n");
        sb.append("- dataSource: 数据来源（如：第三方接口、合作方系统等）\n");
        sb.append("- httpMethod: 请求方式（GET/POST/PUT/DELETE）\n");
        sb.append("- requestUrl: 请求URL路径\n");
        sb.append("- protocol: 协议（HTTP/HTTPS）\n");
        sb.append("- dataFormat: 数据格式（JSON/XML/Form）\n");
        sb.append("- apiDescription: 接口描述\n");
        sb.append("- remark: 备注\n\n");

        sb.append("### 第二部分：请求参数\n");
        sb.append("请求参数分为两部分：\n\n");

        sb.append("#### 请求头（requestHeaders）\n");
        sb.append("固定的请求头字段：\n");
        sb.append("- 调用系统 (callSystem) - 标识调用方系统\n");
        sb.append("- 查询标识 (queryId) - 唯一查询标识\n");
        sb.append("如果文档中有额外的请求头，也请提取出来。\n\n");

        sb.append("#### 请求参数（requestParams）\n");
        sb.append("每个字段需要包含：\n");
        sb.append("- fieldNameEn: 字段英文名（文档中提供的）\n");
        sb.append("- fieldDescription: 字段描述（中文说明）\n");
        sb.append("- fieldType: 类型（String/Integer/Long/BigDecimal/Boolean/Date/Object/Array）\n");
        sb.append("- length: 长度/精度（如：50, 18,6 等，无则填\"\"）\n");
        sb.append("- required: 是否必传（Y/N）\n");
        sb.append("- fieldNameEn2: 字段英文名（同上）\n");
        sb.append("- fieldNameEn3: 字段英文名（同上）\n");
        sb.append("- children: 嵌套子字段（当type为Object或Array时）\n\n");

        sb.append("### 第三部分：响应参数\n");
        sb.append("响应参数分为两部分：\n\n");

        sb.append("#### 响应公共体（responseCommonBody）\n");
        sb.append("Java统一响应体规范，通常包含：\n");
        sb.append("- code: 状态码（Integer）\n");
        sb.append("- message: 消息（String）\n");
        sb.append("- data: 数据体（Object）\n");
        sb.append("请根据文档实际响应结构调整。\n\n");

        sb.append("#### 响应业务参数（responseParams）\n");
        sb.append("每个字段需要包含：\n");
        sb.append("- fieldNameEn: 字段英文名（文档中提供的）\n");
        sb.append("- fieldDescription: 字段描述（中文说明）\n");
        sb.append("- fieldType: 类型\n");
        sb.append("- length: 长度\n");
        sb.append("- required: 是否必传（Y/N）\n");
        sb.append("- fieldNameEn2: 字段英文名（同上）\n");
        sb.append("- fieldNameEn3: 字段英文名（同上）\n");
        sb.append("- children: 嵌套子字段\n\n");

        sb.append("## 输出格式要求\n");
        sb.append("请严格按照以下JSON结构输出，不要添加任何markdown代码块标记或其他说明文字：\n\n");
        sb.append("""
{
  "interfaceInfo": {
    "internalApiName": "内部接口名",
    "externalApiName": "外部接口名",
    "companyCode": "公司简称",
    "dataSource": "数据来源",
    "httpMethod": "POST",
    "requestUrl": "/api/example",
    "protocol": "HTTPS",
    "dataFormat": "JSON",
    "apiDescription": "接口描述",
    "remark": ""
  },
  "requestHeaders": [
    {
      "fieldNameEn": "callSystem",
      "fieldDescription": "调用系统",
      "fieldType": "String",
      "length": "50",
      "required": "Y",
      "fieldNameEn2": "callSystem",
      "fieldNameEn3": "callSystem"
    },
    {
      "fieldNameEn": "queryId",
      "fieldDescription": "查询标识",
      "fieldType": "String",
      "length": "64",
      "required": "Y",
      "fieldNameEn2": "queryId",
      "fieldNameEn3": "queryId"
    }
  ],
  "requestParams": [
    {
      "fieldNameEn": "字段英文名",
      "fieldDescription": "字段描述",
      "fieldType": "String",
      "length": "",
      "required": "Y",
      "fieldNameEn2": "字段英文名",
      "fieldNameEn3": "字段英文名",
      "children": []
    }
  ],
  "responseCommonBody": [
    {
      "fieldNameEn": "code",
      "fieldDescription": "状态码",
      "fieldType": "Integer",
      "length": "",
      "required": "Y",
      "fieldNameEn2": "code",
      "fieldNameEn3": "code"
    },
    {
      "fieldNameEn": "message",
      "fieldDescription": "响应消息",
      "fieldType": "String",
      "length": "",
      "required": "Y",
      "fieldNameEn2": "message",
      "fieldNameEn3": "message"
    },
    {
      "fieldNameEn": "data",
      "fieldDescription": "数据体",
      "fieldType": "Object",
      "length": "",
      "required": "Y",
      "fieldNameEn2": "data",
      "fieldNameEn3": "data",
      "children": []
    }
  ],
  "responseParams": [
    {
      "fieldNameEn": "字段英文名",
      "fieldDescription": "字段描述",
      "fieldType": "String",
      "length": "",
      "required": "Y",
      "fieldNameEn2": "字段英文名",
      "fieldNameEn3": "字段英文名",
      "children": []
    }
  ]
}
""");

        sb.append("\n## 文档内容\n").append("{{documentContent}}");

        return sb.toString();
    }

    /**
     * 推断文件类型
     */
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

    /**
     * 推断文档类型
     */
    private String inferDocumentType(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("swagger") || lower.contains("openapi")) return "Swagger/OpenAPI";
        if (lower.contains("postman")) return "Postman";
        if (text.contains("请求参数") || text.contains("请求报文") || text.contains("响应报文")) return "中文接口文档";
        if (text.contains("request") && text.contains("response")) return "API文档";
        return "通用文档";
    }

    /**
     * 截断过长的文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n\n...（文档过长，已截断，总计" + text.length() + "字符）";
    }

    /**
     * 解析AI返回的JSON响应
     */
    private ParseResult parseAiResponse(String aiResponse, String originalText) {
        try {
            // 尝试从response中提取JSON（可能包含markdown代码块）
            String jsonStr = extractJsonFromResponse(aiResponse);

            if (jsonStr != null) {
                try {
                    ParseResult result = objectMapper.readValue(jsonStr, ParseResult.class);
                    if (result != null) {
                        result.setRawParsedText(originalText);
                        log.info("AI解析结果JSON解析成功");
                        return result;
                    }
                } catch (JsonProcessingException e) {
                    log.warn("AI返回的JSON格式有误，尝试修复: {}", e.getMessage());
                }
            }

            // 如果JSON解析失败，尝试构造部分结果
            log.warn("无法从AI响应中提取有效JSON，返回部分结果");
            return buildPartialResult(aiResponse, originalText);

        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage());
            return buildPartialResult(aiResponse, originalText);
        }
    }

    /**
     * 从AI响应中提取JSON字符串
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return null;

        // 尝试匹配 ```json ... ``` 代码块
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.MULTILINE);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(response);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1).trim();
        }

        // 尝试匹配最外层的 {...} 对象
        int braceStart = response.indexOf('{');
        if (braceStart >= 0) {
            int braceEnd = response.lastIndexOf('}');
            if (braceEnd > braceStart) {
                return response.substring(braceStart, braceEnd + 1);
            }
        }

        return null;
    }

    /**
     * 构建部分解析结果
     */
    private ParseResult buildPartialResult(String aiResponse, String originalText) {
        return ParseResult.builder()
                .interfaceInfo(InterfaceInfo.builder()
                        .internalApiName("（AI解析失败，请手动填写）")
                        .externalApiName("（AI解析失败，请手动填写）")
                        .build())
                .requestHeaders(Arrays.asList(
                        createDefaultField("callSystem", "调用系统", "String", "50", "Y"),
                        createDefaultField("queryId", "查询标识", "String", "64", "Y")
                ))
                .responseCommonBody(Arrays.asList(
                        createDefaultField("code", "状态码", "Integer", "", "Y"),
                        createDefaultField("message", "响应消息", "String", "", "Y"),
                        createDefaultField("data", "数据体", "Object", "", "Y")
                ))
                .requestParams(new ArrayList<>())
                .responseParams(new ArrayList<>())
                .rawParsedText(originalText)
                .build();
    }

    /**
     * 降级解析（不依赖AI，仅做基础文本分析）
     */
    private ParseResult fallbackParse(String documentText, String fileName) {
        log.info("使用降级方案解析文档: {}", fileName);

        return ParseResult.builder()
                .interfaceInfo(InterfaceInfo.builder()
                        .internalApiName("（请手动填写）")
                        .externalApiName("（请手动填写）")
                        .companyCode("（请手动填写）")
                        .dataSource("第三方接口")
                        .httpMethod("POST")
                        .dataFormat("JSON")
                        .build())
                .requestHeaders(Arrays.asList(
                        createDefaultField("callSystem", "调用系统", "String", "50", "Y"),
                        createDefaultField("queryId", "查询标识", "String", "64", "Y")
                ))
                .responseCommonBody(Arrays.asList(
                        createDefaultField("code", "状态码", "Integer", "", "Y"),
                        createDefaultField("message", "响应消息", "String", "", "Y"),
                        createDefaultField("data", "数据体", "Object", "", "Y")
                ))
                .requestParams(extractFieldsByRegex(documentText, false))
                .responseParams(extractFieldsByRegex(documentText, true))
                .rawParsedText(documentText)
                .build();
    }

    /**
     * 通过正则表达式尝试提取字段（降级方案）
     */
    private List<FieldDefinition> extractFieldsByRegex(String text, boolean isResponse) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (text == null || text.isBlank()) return fields;

        // 尝试匹配常见的字段定义模式
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
                fields.add(createDefaultField(name, desc, type, "", "N"));
            }
        }

        return fields;
    }

    /**
     * 创建默认字段定义
     */
    private FieldDefinition createDefaultField(String nameEn, String desc, String type, String length, String required) {
        return FieldDefinition.builder()
                .fieldNameEn(nameEn)
                .fieldDescription(desc)
                .fieldType(type)
                .length(length)
                .required(required)
                .fieldNameEn2(nameEn)
                .fieldNameEn3(nameEn)
                .children(new ArrayList<>())
                .build();
    }
}
