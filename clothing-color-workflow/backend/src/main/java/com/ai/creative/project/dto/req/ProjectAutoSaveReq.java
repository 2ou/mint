package com.ai.creative.project.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectAutoSaveReq {
    @NotBlank
    private String canvasJson;
    private String flowJson;
    private String configJson;
}
