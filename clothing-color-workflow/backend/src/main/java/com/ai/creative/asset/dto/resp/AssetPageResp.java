package com.ai.creative.asset.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetPageResp {
    private Long id;
    private String assetCode;
    private Long projectId;
    private String sourceType;
    private String assetType;
    private String bizType;
    private String fileName;
    private Long fileSize;
    private String ossUrl;
    private LocalDateTime createTime;
}
