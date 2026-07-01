package com.ai.dto;

import com.ai.entity.AplusProject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A+ 项目响应 DTO
 */
@Data
public class AplusProjectResponse {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Long id;
    private String projectName;
    private String spu;
    private String referenceImageUrl;
    private String sellingPoints;
    private String aplusMarkdown;
    private Long layoutTemplateId;
    private String layoutTemplateName;
    private String layoutReferenceImageUrl;
    private String layoutBlueprintJson;
    private String selectedModules;
    private List<String> selectedModuleList;
    private String status;
    private String errorMessage;
    private String operator;
    private String shopName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private List<AplusImageTaskResponse> imageTasks;

    public static AplusProjectResponse from(AplusProject project) {
        AplusProjectResponse resp = new AplusProjectResponse();
        resp.setId(project.getId());
        resp.setProjectName(project.getProjectName());
        resp.setSpu(project.getSpu());
        resp.setReferenceImageUrl(project.getReferenceImageUrl());
        resp.setSellingPoints(project.getSellingPoints());
        resp.setAplusMarkdown(project.getAplusMarkdown());
        resp.setLayoutTemplateId(project.getLayoutTemplateId());
        resp.setLayoutTemplateName(project.getLayoutTemplateName());
        resp.setLayoutReferenceImageUrl(project.getLayoutReferenceImageUrl());
        resp.setLayoutBlueprintJson(project.getLayoutBlueprintJson());
        resp.setSelectedModules(project.getSelectedModules());
        resp.setSelectedModuleList(parseSelectedModules(project.getSelectedModules()));
        resp.setStatus(project.getStatus());
        resp.setErrorMessage(project.getErrorMessage());
        resp.setOperator(project.getOperator());
        resp.setShopName(project.getShopName());
        resp.setCreatedAt(project.getCreatedAt());
        resp.setCompletedAt(project.getCompletedAt());
        if (project.getImageTasks() != null) {
            resp.setImageTasks(project.getImageTasks().stream()
                    .map(AplusImageTaskResponse::from)
                    .collect(Collectors.toList()));
        }
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
}
