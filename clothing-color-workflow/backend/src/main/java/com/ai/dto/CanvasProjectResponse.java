package com.ai.dto;

import com.ai.entity.CanvasProject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Data
public class CanvasProjectResponse {
    private Long id;
    private String projectName;
    private String operator;
    private String shopName;
    private Map<String, String> snapshot;
    private Map<String, Object> meta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CanvasProjectResponse from(CanvasProject project, ObjectMapper objectMapper, boolean includeSnapshot) {
        CanvasProjectResponse response = new CanvasProjectResponse();
        response.setId(project.getId());
        response.setProjectName(project.getProjectName());
        response.setOperator(project.getOperator());
        response.setShopName(project.getShopName());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        response.setMeta(readObject(project.getMetaJson(), objectMapper));
        response.setSnapshot(includeSnapshot ? readSnapshot(project.getSnapshotJson(), objectMapper) : Collections.emptyMap());
        return response;
    }

    private static Map<String, String> readSnapshot(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Map<String, Object> readObject(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
