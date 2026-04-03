package com.ai.creative.run.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NodeRunSelectOutputReq {
    @NotNull
    private Long assetId;
}
