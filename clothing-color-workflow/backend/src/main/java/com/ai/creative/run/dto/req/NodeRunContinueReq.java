package com.ai.creative.run.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NodeRunContinueReq {
    @NotNull
    private Long projectId;
    @NotNull
    private Long fromRunId;
    @NotNull
    private NodeRunReq nextNode;
}
