package com.ai.dto;

import com.ai.entity.TemplateLabProject;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TemplateLabProjectResponse {
    private Long id;
    private String projectName;
    private String templateId;
    private String templateDefinitionJson;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private String designJson;
    private String thumbnailDataUrl;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TemplateLabProjectResponse from(TemplateLabProject project, boolean includeDesign) {
        return TemplateLabProjectResponse.builder()
                .id(project.getId())
                .projectName(project.getProjectName())
                .templateId(project.getTemplateId())
                .templateDefinitionJson(includeDesign ? project.getTemplateDefinitionJson() : null)
                .canvasWidth(project.getCanvasWidth())
                .canvasHeight(project.getCanvasHeight())
                .designJson(includeDesign ? project.getDesignJson() : null)
                .thumbnailDataUrl(project.getThumbnailDataUrl())
                .version(project.getVersion())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
