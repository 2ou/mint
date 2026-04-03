package com.ai.creative.asset.dto.req;

import lombok.Data;

@Data
public class AssetPageReq {
    private long pageNo = 1;
    private long pageSize = 10;
    private Long projectId;
    private String assetType;
    private String bizType;
}
