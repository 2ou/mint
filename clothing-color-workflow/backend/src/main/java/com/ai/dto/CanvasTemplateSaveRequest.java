package com.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CanvasTemplateSaveRequest {
    private Long id;
    private String templateName;
    private String category;
    private String coverImageUrl;
    private String description;
    private List<String> tags;
    private Map<String, String> snapshot;
    private Map<String, Object> meta;
}
