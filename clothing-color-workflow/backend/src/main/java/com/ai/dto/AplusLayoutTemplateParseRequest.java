package com.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AplusLayoutTemplateParseRequest {
    private String templateName;
    private String layoutReferenceImageUrl;
    /** GPT 5.6 文本模型：gpt-5.6-sol / gpt-5.6-terra / gpt-5.6-luna */
    private String textModel;
    private String notes;
    private String templateStatus;
    private List<String> selectedModules;
}
