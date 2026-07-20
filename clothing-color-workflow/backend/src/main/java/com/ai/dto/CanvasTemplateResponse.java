package com.ai.dto;

import com.ai.entity.CanvasTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class CanvasTemplateResponse {
    private Long id;
    private String templateName;
    private String category;
    private String coverImageUrl;
    private String description;
    private String operator;
    private String shopName;
    private List<String> tags;
    private Map<String, String> snapshot;
    private Map<String, Object> meta;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CanvasTemplateResponse from(CanvasTemplate template, ObjectMapper objectMapper, boolean includeSnapshot) {
        CanvasTemplateResponse response = new CanvasTemplateResponse();
        response.setId(template.getId());
        response.setTemplateName(template.getTemplateName());
        response.setCategory(template.getCategory());
        response.setCoverImageUrl(template.getCoverImageUrl());
        response.setDescription(template.getDescription());
        response.setOperator(template.getOperator());
        response.setShopName(template.getShopName());
        response.setCreatedAt(template.getCreatedAt());
        response.setUpdatedAt(template.getUpdatedAt());
        response.setTags(readList(template.getTagsJson(), objectMapper));
        response.setMeta(readObject(template.getMetaJson(), objectMapper));
        response.setSnapshot(includeSnapshot ? readSnapshot(template.getSnapshotJson(), objectMapper) : Collections.emptyMap());
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

    private static List<String> readList(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
