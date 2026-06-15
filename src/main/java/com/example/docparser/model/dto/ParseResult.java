package com.example.docparser.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 解析结果 - 对应Excel的三部分结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    // ========== 第一部分：接口基本信息 ==========
    /** 接口基本信息 */
    private InterfaceInfo interfaceInfo;

    // ========== 第二部分：请求参数 ==========
    /** 请求头 */
    private List<FieldDefinition> requestHeaders;

    /** 请求体参数 */
    private List<FieldDefinition> requestParams;

    // ========== 第三部分：响应参数 ==========
    /** 响应公共体（统一响应体） */
    private List<FieldDefinition> responseCommonBody;

    /** 响应业务参数 */
    private List<FieldDefinition> responseParams;

    /** 原始解析文本（调试用） */
    private String rawParsedText;
}
