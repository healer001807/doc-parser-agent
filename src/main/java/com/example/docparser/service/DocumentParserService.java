package com.example.docparser.service;

import com.example.docparser.model.dto.ParseResult;
import com.example.docparser.model.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档解析服务 - 负责文档上传、解析协调
 */
public interface DocumentParserService {

    /**
     * 上传并解析文档
     *
     * @param file   上传的文件
     * @param userId 用户ID
     * @return 文档实体
     */
    Document uploadAndParse(MultipartFile file, Long userId);

    /**
     * 重新解析文档
     *
     * @param documentId 文档ID
     * @return 文档实体
     */
    Document reparse(Long documentId);

    /**
     * 获取用户的文档列表
     */
    List<Document> getUserDocuments(Long userId);

    /**
     * 获取文档详情
     */
    Document getDocument(Long documentId);

    /**
     * 获取解析结果
     */
    ParseResult getParseResult(Long documentId);

    /**
     * 删除文档
     */
    void deleteDocument(Long documentId);
}
