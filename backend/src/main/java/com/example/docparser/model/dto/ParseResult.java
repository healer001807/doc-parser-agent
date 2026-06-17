package com.example.docparser.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {
    private InterfaceInfo interfaceInfo;
    private List<FieldDefinition> requestHeaders;
    private List<FieldDefinition> requestBody;
    private List<FieldDefinition> responseCodes;
    private List<FieldDefinition> responseBody;
    private String rawParsedText;
}
