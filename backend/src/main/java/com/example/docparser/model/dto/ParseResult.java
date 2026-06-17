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
    private List<FieldDefinition> requestParams;
    private List<FieldDefinition> responseParams;
    private String rawParsedText;
}
