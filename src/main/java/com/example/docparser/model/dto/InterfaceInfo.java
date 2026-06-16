package com.example.docparser.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接口基本信息 - Excel第一部分
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterfaceInfo {

    /** 内部接口名 */
    private String internalApiName;

    /** 外部接口名 */
    private String externalApiName;

    /** 第三方公司简称 */
    private String companyCode;

    /** 数据来源 */
    private String dataSource;

    /** 接口描述 */
    private String apiDescription;

    /** 请求方式（GET/POST/PUT/DELETE等） */
    private String httpMethod;

    /** 请求URL路径 */
    private String requestUrl;

    /** 协议类型（HTTP/HTTPS） */
    private String protocol;

    /** 数据格式（JSON/XML/Form等） */
    private String dataFormat;

    /** 备注 */
    private String remark;
}
