package com.ai.creative.asset.dto.resp;

import lombok.Data;

@Data
public class AssetUploadResp {
    private Long assetId;
    private String assetCode;
    private String ossUrl;
}
