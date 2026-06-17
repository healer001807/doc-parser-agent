package com.example.docparser.service;

import com.example.docparser.model.dto.ParseResult;

/**
 * AI智能解析服务 - 使用大模型解析第三方接口文档
 */
public interface AiParserService {

    /**
     * 使用AI解析文档文本内容，提取结构化接口信息
     *
     * @param documentText 文档的原始文本内容
     * @param fileName     原始文件名（用于推断文档类型）
     * @return 解析后的结构化结果
     */
    ParseResult parseDocument(String documentText, String fileName);

    /**
     * 根据已有文本和用户补充信息重新解析
     *
     * @param documentText  文档的原始文本内容
     * @param fileName      原始文件名
     * @param userHint      用户提示/补充说明
     * @return 解析后的结构化结果
     */
    ParseResult parseDocumentWithHint(String documentText, String fileName, String userHint);
}
