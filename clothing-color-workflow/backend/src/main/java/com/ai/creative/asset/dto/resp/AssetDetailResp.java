package com.ai.creative.asset.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetDetailResp {
    private Long id;
    private String assetCode;
    private Long projectId;
    private Long projectVersionId;
    private String sourceType;
    private String assetType;
    private String bizType;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String ossUrl;
    private String metadataJson;
    private LocalDateTime createTime;
}
