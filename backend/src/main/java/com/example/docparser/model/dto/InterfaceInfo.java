package com.example.docparser.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterfaceInfo {
    private String apiNameCn;
    private String dataImportMethod;
    private String apiCallFrequency;
    private String externalVendorUrl;
    private String externalDataUrl;
    private String apiNameEn;
    private String apiUrl;
}
