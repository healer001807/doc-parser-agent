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
public class FieldDefinition {
    private String fieldNameEn;
    private String fieldNameCn;
    private String fieldType;
    private String fieldLength;
    private String required;
    private String remark;
    private String fieldNameEn2;
    private String externalFieldName;
    private List<FieldDefinition> children;
}
