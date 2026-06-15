package com.example.docparser.controller;

import com.example.docparser.model.dto.ParseResult;
import com.example.docparser.model.entity.Document;
import com.example.docparser.model.vo.ApiResponse;
import com.example.docparser.service.DocumentParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentParserService documentParserService;

    /**
     * 上传并解析文档
     * POST /api/documents/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Document>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "请选择要上传的文件"));
        }

        Long userId = (Long) authentication.getPrincipal();
        log.info("用户[{}]上传文档: {}, 大小: {}KB",
                userId, file.getOriginalFilename(), file.getSize() / 1024);

        try {
            Document document = documentParserService.uploadAndParse(file, userId);
            return ResponseEntity.ok(ApiResponse.success("文档上传并解析完成", document));
        } catch (Exception e) {
            log.error("文档上传失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "文档处理失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户的文档列表
     * GET /api/documents/list
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Document>>> getDocumentList(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<Document> documents = documentParserService.getUserDocuments(userId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    /**
     * 获取文档详情
     * GET /api/documents/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Document>> getDocument(@PathVariable Long id) {
        try {
            Document document = documentParserService.getDocument(id);
            return ResponseEntity.ok(ApiResponse.success(document));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 获取解析结果
     * GET /api/documents/{id}/parse-result
     */
    @GetMapping("/{id}/parse-result")
    public ResponseEntity<ApiResponse<ParseResult>> getParseResult(@PathVariable Long id) {
        try {
            ParseResult result = documentParserService.getParseResult(id);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 重新解析文档
     * POST /api/documents/{id}/reparse
     */
    @PostMapping("/{id}/reparse")
    public ResponseEntity<ApiResponse<Document>> reparseDocument(@PathVariable Long id) {
        try {
            Document document = documentParserService.reparse(id);
            return ResponseEntity.ok(ApiResponse.success("重新解析完成", document));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 下载生成的Excel文件
     * GET /api/documents/{id}/download
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadExcel(@PathVariable Long id) {
        try {
            Document document = documentParserService.getDocument(id);
            if (document.getExcelPath() == null) {
                return ResponseEntity.badRequest().build();
            }

            File excelFile = new File(document.getExcelPath());
            if (!excelFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(excelFile);
            String downloadFileName = document.getOriginalName()
                    .replaceAll("\\.[^.]+$", "") + "_接口文档.xlsx";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + downloadFileName + "\"")
                    .body(resource);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除文档
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id) {
        try {
            documentParserService.deleteDocument(id);
            return ResponseEntity.ok(ApiResponse.success("删除成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
