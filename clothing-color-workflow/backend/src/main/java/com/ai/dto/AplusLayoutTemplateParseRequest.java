package com.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AplusLayoutTemplateParseRequest {
    private String templateName;
    private String layoutReferenceImageUrl;
    private String textModel;
    private String notes;
    private String templateStatus;
    private List<String> selectedModules;
}
