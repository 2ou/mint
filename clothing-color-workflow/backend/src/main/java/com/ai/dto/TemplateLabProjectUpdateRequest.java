package com.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateLabProjectUpdateRequest {
    @Size(max = 160)
    private String projectName;
    private Integer canvasWidth;
    private Integer canvasHeight;
    private String designJson;
    private String thumbnailDataUrl;
}
