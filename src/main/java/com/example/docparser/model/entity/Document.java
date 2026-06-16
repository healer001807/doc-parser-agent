package com.example.docparser.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 原始文件名 */
    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    /** 存储文件名（UUID重命名） */
    @Column(name = "stored_name", nullable = false, length = 500)
    private String storedName;

    /** 文件类型：pdf, word, html, txt */
    @Column(name = "file_type", length = 20)
    private String fileType;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 存储路径 */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    /** 解析状态：PENDING, PARSING, COMPLETED, FAILED */
    @Column(name = "parse_status", length = 20)
    @Builder.Default
    private String parseStatus = "PENDING";

    /** 解析后的原始文本内容 */
    @Column(name = "parsed_content", columnDefinition = "TEXT")
    private String parsedContent;

    /** 解析后的结构化JSON结果 */
    @Column(name = "parse_result_json", columnDefinition = "TEXT")
    private String parseResultJson;

    /** 生成的Excel文件路径 */
    @Column(name = "excel_path", length = 1000)
    private String excelPath;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 第三方公司简称 */
    @Column(name = "company_code", length = 100)
    private String companyCode;

    /** 内部接口名 */
    @Column(name = "internal_api_name", length = 500)
    private String internalApiName;

    /** 外部接口名 */
    @Column(name = "external_api_name", length = 500)
    private String externalApiName;

    /** 数据来源 */
    @Column(name = "data_source", length = 200)
    private String dataSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
