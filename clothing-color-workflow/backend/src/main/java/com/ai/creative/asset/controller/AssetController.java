package com.ai.creative.asset.controller;

import com.ai.creative.asset.dto.req.AssetPageReq;
import com.ai.creative.asset.dto.resp.AssetDetailResp;
import com.ai.creative.asset.dto.resp.AssetUploadResp;
import com.ai.creative.asset.service.AssetService;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/creative/assets")
@RequiredArgsConstructor
public class AssetController {
    private final AssetService assetService;

    @PostMapping("/upload")
    public ApiResponse<AssetUploadResp> upload(@RequestParam MultipartFile file,
                                                @RequestParam(required = false) Long projectId,
                                                @RequestParam String assetType,
                                                @RequestParam String bizType){
        return ApiResponse.ok("ok", assetService.upload(file, projectId, assetType, bizType));
    }
    @GetMapping("/page")
    public ApiResponse<?> page(AssetPageReq req){ return ApiResponse.ok("ok", assetService.page(req)); }
    @GetMapping("/{assetId}")
    public ApiResponse<AssetDetailResp> detail(@PathVariable Long assetId){ return ApiResponse.ok("ok", assetService.detail(assetId)); }
    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> delete(@PathVariable Long assetId){ assetService.deleteAsset(assetId); return ApiResponse.ok("ok", null); }
}
