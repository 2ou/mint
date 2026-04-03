package com.ai.creative.template.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateCreateProjectReq {
    @NotBlank
    private String projectName;
    private String description;
}
