package com.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CanvasProjectSaveRequest {
    private Long id;
    private String projectName;
    private Map<String, String> snapshot;
    private Map<String, Object> meta;
}
