package com.ai.creative.project.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCopyReq {
    @NotBlank
    private String projectName;
}
