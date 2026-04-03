package com.ai.creative.asset.service;

import com.ai.creative.asset.dto.req.AssetPageReq;
import com.ai.creative.asset.dto.resp.AssetDetailResp;
import com.ai.creative.asset.dto.resp.AssetPageResp;
import com.ai.creative.asset.dto.resp.AssetUploadResp;
import com.ai.creative.common.PageResult;
import org.springframework.web.multipart.MultipartFile;

public interface AssetService {
    AssetUploadResp upload(MultipartFile file, Long projectId, String assetType, String bizType);
    PageResult<AssetPageResp> page(AssetPageReq req);
    AssetDetailResp detail(Long assetId);
    void deleteAsset(Long assetId);
}
