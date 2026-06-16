package com.example.docparser.service;

import com.example.docparser.model.dto.ParseResult;
import com.example.docparser.model.entity.Document;
import com.example.docparser.repository.DocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserServiceImpl implements DocumentParserService {

    private final DocumentRepository documentRepository;
    private final AiParserService aiParserService;
    private final ExcelGeneratorService excelGeneratorService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public Document uploadAndParse(MultipartFile file, Long userId) {
        try {
            // 1. 保存文件
            String originalName = file.getOriginalFilename();
            String storedName = UUID.randomUUID().toString() + "_" + originalName;
            String fileType = getFileExtension(originalName);
            Path uploadPath = Paths.get(uploadDir, userId.toString());
            Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 2. 创建文档记录
            Document document = Document.builder()
                    .userId(userId)
                    .originalName(originalName)
                    .storedName(storedName)
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .filePath(filePath.toString())
                    .parseStatus("PARSING")
                    .build();
            document = documentRepository.save(document);

            // 3. 解析文档内容（提取文本）
            String extractedText = extractText(filePath.toFile());

            // 4. AI智能解析
            try {
                ParseResult parseResult = aiParserService.parseDocument(extractedText, originalName);
                document.setParsedContent(extractedText);
                document.setParseResultJson(objectMapper.writeValueAsString(parseResult));
                document.setParseStatus("COMPLETED");

                // 更新接口基本信息到文档记录
                if (parseResult.getInterfaceInfo() != null) {
                    document.setInternalApiName(parseResult.getInterfaceInfo().getInternalApiName());
                    document.setExternalApiName(parseResult.getInterfaceInfo().getExternalApiName());
                    document.setCompanyCode(parseResult.getInterfaceInfo().getCompanyCode());
                    document.setDataSource(parseResult.getInterfaceInfo().getDataSource());
                }

                // 5. 生成Excel
                String excelPath = excelGeneratorService.generateExcel(document.getId(), parseResult, originalName);
                document.setExcelPath(excelPath);

            } catch (Exception e) {
                log.error("AI解析失败: {}", e.getMessage(), e);
                document.setParseStatus("FAILED");
                document.setErrorMessage("解析失败: " + e.getMessage());
                document.setParsedContent(extractedText);
            }

            document.setUpdatedAt(LocalDateTime.now());
            return documentRepository.save(document);

        } catch (Exception e) {
            log.error("文档处理失败: {}", e.getMessage(), e);
            Document doc = Document.builder()
                    .userId(userId)
                    .originalName(file.getOriginalFilename())
                    .fileType(getFileExtension(file.getOriginalFilename()))
                    .fileSize(file.getSize())
                    .parseStatus("FAILED")
                    .errorMessage("文档处理失败: " + e.getMessage())
                    .build();
            return documentRepository.save(doc);
        }
    }

    @Override
    @Transactional
    public Document reparse(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在：" + documentId));

        try {
            document.setParseStatus("PARSING");
            document.setErrorMessage(null);
            document = documentRepository.save(document);

            File file = new File(document.getFilePath());
            String extractedText = extractText(file);

            ParseResult parseResult = aiParserService.parseDocument(extractedText, document.getOriginalName());
            document.setParsedContent(extractedText);
            document.setParseResultJson(objectMapper.writeValueAsString(parseResult));
            document.setParseStatus("COMPLETED");

            if (parseResult.getInterfaceInfo() != null) {
                document.setInternalApiName(parseResult.getInterfaceInfo().getInternalApiName());
                document.setExternalApiName(parseResult.getInterfaceInfo().getExternalApiName());
                document.setCompanyCode(parseResult.getInterfaceInfo().getCompanyCode());
                document.setDataSource(parseResult.getInterfaceInfo().getDataSource());
            }

            String excelPath = excelGeneratorService.generateExcel(document.getId(), parseResult, document.getOriginalName());
            document.setExcelPath(excelPath);

        } catch (Exception e) {
            log.error("重新解析失败: {}", e.getMessage(), e);
            document.setParseStatus("FAILED");
            document.setErrorMessage("重新解析失败: " + e.getMessage());
        }

        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    @Override
    public List<Document> getUserDocuments(Long userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Document getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在：" + documentId));
    }

    @Override
    public ParseResult getParseResult(Long documentId) {
        Document document = getDocument(documentId);
        if (document.getParseResultJson() == null) {
            throw new RuntimeException("该文档尚未解析完成");
        }
        try {
            return objectMapper.readValue(document.getParseResultJson(), ParseResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析结果JSON格式错误", e);
        }
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = getDocument(documentId);
        // 删除物理文件
        try {
            if (document.getFilePath() != null) {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            }
            if (document.getExcelPath() != null) {
                Files.deleteIfExists(Paths.get(document.getExcelPath()));
            }
        } catch (IOException e) {
            log.warn("删除文件失败: {}", e.getMessage());
        }
        documentRepository.delete(document);
    }

    // ==================== 文本提取方法 ====================

    /**
     * 从文件中提取文本内容
     */
    private String extractText(File file) throws IOException {
        String name = file.getName().toLowerCase();

        try {
            if (name.endsWith(".pdf")) {
                return extractPdfText(file);
            } else if (name.endsWith(".docx")) {
                return extractDocxText(file);
            } else if (name.endsWith(".doc")) {
                return extractDocText(file);
            } else if (name.endsWith(".html") || name.endsWith(".htm")) {
                return extractHtmlText(file);
            } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                return Files.readString(file.toPath());
            } else {
                // 使用Tika兜底
                return extractWithTika(file);
            }
        } catch (Exception e) {
            log.warn("专用提取器失败，降级使用Tika: {}", e.getMessage());
            return extractWithTika(file);
        }
    }

    /**
     * 提取PDF文本
     */
    private String extractPdfText(File file) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                     org.apache.pdfbox.Loader.loadPDF(file)) {
            org.apache.pdfbox.text.PDFTextStripper stripper =
                    new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * 提取DOCX文本
     */
    private String extractDocxText(File file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
            return extractor.getText();
        }
    }

    /**
     * 提取DOC文本
     */
    private String extractDocText(File file) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(new FileInputStream(file))) {
            WordExtractor extractor = new WordExtractor(doc);
            return extractor.getText();
        }
    }

    /**
     * 提取HTML文本
     */
    private String extractHtmlText(File file) throws IOException {
        String content = Files.readString(file.toPath());
        // 简单去除HTML标签
        return content.replaceAll("<[^>]*>", " ")
                .replaceAll("&[^;]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 使用Apache Tika提取文本（通用方案）
     */
    private String extractWithTika(File file) throws IOException {
        try {
            Tika tika = new Tika();
            return tika.parseToString(file);
        } catch (TikaException e) {
            throw new IOException("Tika解析失败", e);
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "unknown";
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "unknown";
    }
}
