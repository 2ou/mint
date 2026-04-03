package com.ai.creative.run.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NodeRunReq {
    @NotNull
    private Long projectId;
    private Long projectVersionId;
    @NotBlank
    private String nodeId;
    private String nodeName;
    @NotBlank
    private String nodeType;
    private String provider;
    private String modelCode;
    private String runMode;
    private String inputJson;
    private String requestJson;
}
