package com.ai.dto;

import com.ai.entity.AplusTemplate;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A+ 模板响应 DTO
 */
@Data
public class AplusTemplateResponse {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Long id;
    private String templateName;
    private String spu;
    private String referenceImageUrl;
    private String sellingPoints;
    private String selectedModules;
    private List<String> selectedModuleList;
    private String moduleExtras;
    private Map<String, AplusModuleExtra> moduleExtrasMap;
    private String operator;
    private String shopName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public static AplusTemplateResponse from(AplusTemplate template) {
        AplusTemplateResponse resp = new AplusTemplateResponse();
        resp.setId(template.getId());
        resp.setTemplateName(template.getTemplateName());
        resp.setSpu(template.getSpu());
        resp.setReferenceImageUrl(template.getReferenceImageUrl());
        resp.setSellingPoints(template.getSellingPoints());
        resp.setSelectedModules(template.getSelectedModules());
        resp.setSelectedModuleList(parseSelectedModules(template.getSelectedModules()));
        resp.setModuleExtras(template.getModuleExtras());
        resp.setModuleExtrasMap(parseModuleExtras(template.getModuleExtras()));
        resp.setOperator(template.getOperator());
        resp.setShopName(template.getShopName());
        resp.setCreatedAt(template.getCreatedAt());
        resp.setUpdatedAt(template.getUpdatedAt());
        return resp;
    }

    private static List<String> parseSelectedModules(String selectedModules) {
        if (selectedModules == null || selectedModules.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(selectedModules, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, AplusModuleExtra> parseModuleExtras(String moduleExtras) {
        if (moduleExtras == null || moduleExtras.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(moduleExtras, new TypeReference<Map<String, AplusModuleExtra>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
