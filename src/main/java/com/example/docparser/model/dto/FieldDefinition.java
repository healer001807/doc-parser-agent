package com.example.docparser.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段定义 - 用于请求参数和响应参数
 * 文档要求：字段英文、字段描述、类型、长度、是否必传、字段英文、字段英文（重复出现符合文档规范）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinition {

    /** 字段英文名（文档提供的） */
    private String fieldNameEn;

    /** 字段描述 */
    private String fieldDescription;

    /** 类型（String, Integer, Long, BigDecimal, Boolean, Date, Object, Array等） */
    private String fieldType;

    /** 长度 */
    private String length;

    /** 是否必传：Y/N */
    private String required;

    /** 字段英文名（文档提供的）- 第2列，与fieldNameEn相同 */
    private String fieldNameEn2;

    /** 字段英文名（文档提供的）- 第3列，与fieldNameEn相同 */
    private String fieldNameEn3;

    /** 子字段（嵌套对象/数组时使用） */
    private java.util.List<FieldDefinition> children;
}
