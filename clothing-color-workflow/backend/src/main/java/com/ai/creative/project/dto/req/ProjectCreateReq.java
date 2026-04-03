package com.ai.creative.project.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCreateReq {
    @NotBlank
    private String projectName;
    private Long sourceTemplateId;
    private String description;
    private String coverUrl;
}
