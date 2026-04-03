package com.ai.creative.template.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateSaveReq {
    @NotBlank
    private String templateName;
    private String category;
    private String description;
    private String coverUrl;
}
