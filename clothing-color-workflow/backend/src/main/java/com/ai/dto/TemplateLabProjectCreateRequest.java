package com.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateLabProjectCreateRequest {
    @Size(max = 160)
    private String projectName;

    @NotBlank
    @Size(max = 80)
    private String templateId;
}
