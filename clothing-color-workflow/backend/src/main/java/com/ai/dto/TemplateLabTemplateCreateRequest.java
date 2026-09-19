package com.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateLabTemplateCreateRequest {
    @NotBlank
    @Size(max = 160)
    private String name;

    @Size(max = 80)
    private String category;

    @Size(max = 500)
    private String description;

    @NotBlank
    private String definitionJson;

    private String thumbnailDataUrl;
}
