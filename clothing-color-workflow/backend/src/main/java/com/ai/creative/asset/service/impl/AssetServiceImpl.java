package com.ai.creative.asset.service.impl;

import com.ai.creative.asset.convert.AssetConvert;
import com.ai.creative.asset.dto.req.AssetPageReq;
import com.ai.creative.asset.dto.resp.AssetDetailResp;
import com.ai.creative.asset.dto.resp.AssetPageResp;
import com.ai.creative.asset.dto.resp.AssetUploadResp;
import com.ai.creative.asset.entity.CreativeAsset;
import com.ai.creative.asset.mapper.CreativeAssetMapper;
import com.ai.creative.asset.service.AssetService;
import com.ai.creative.common.CodeGenUtils;
import com.ai.creative.common.CreativeAsserts;
import com.ai.creative.common.PageResult;
import com.ai.creative.enums.AssetSourceTypeEnum;
import com.ai.creative.oss.CreativeOssService;
import com.ai.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {
    private final CreativeAssetMapper assetMapper;
    private final CreativeOssService ossService;

    @Override
    @Transactional
    public AssetUploadResp upload(MultipartFile file, Long projectId, String assetType, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("file is empty");
        }
        try {
            String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
            String objectKey = ossService.generateObjectKey(bizType, originalName);
            String ossUrl;
            try (InputStream inputStream = file.getInputStream()) {
                ossUrl = ossService.uploadInputStream(objectKey, inputStream);
            }

            Integer width = null;
            Integer height = null;
            if (file.getContentType() != null && file.getContentType().startsWith("image/")) {
                try (InputStream imageStream = file.getInputStream()) {
                    BufferedImage image = ImageIO.read(imageStream);
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                } catch (Exception e) {
                    log.warn("read image size failed: {}", originalName, e);
                }
            }

            String fileExt = null;
            int idx = originalName.lastIndexOf('.');
            if (idx > -1 && idx < originalName.length() - 1) {
                fileExt = originalName.substring(idx + 1).toLowerCase();
            }

            CreativeAsset asset = new CreativeAsset();
            asset.setAssetCode(CodeGenUtils.code("AST"));
            asset.setProjectId(projectId);
            asset.setSourceType(AssetSourceTypeEnum.UPLOAD.name());
            asset.setAssetType(assetType);
            asset.setBizType(bizType);
            asset.setFileName(originalName);
            asset.setFileExt(fileExt);
            asset.setMimeType(file.getContentType());
            asset.setFileSize(file.getSize());
            asset.setWidth(width);
            asset.setHeight(height);
            asset.setOssUrl(ossUrl);
            asset.setDeleted(0);
            asset.setCreateTime(LocalDateTime.now());
            assetMapper.insert(asset);

            AssetUploadResp resp = new AssetUploadResp();
            resp.setAssetId(asset.getId());
            resp.setAssetCode(asset.getAssetCode());
            resp.setOssUrl(asset.getOssUrl());
            return resp;
        } catch (Exception e) {
            log.error("asset upload failed", e);
            throw new BusinessException("upload failed: " + e.getMessage());
        }
    }

    @Override
    public PageResult<AssetPageResp> page(AssetPageReq req) {
        Page<CreativeAsset> page = assetMapper.selectPage(new Page<>(req.getPageNo(), req.getPageSize()),
                new LambdaQueryWrapper<CreativeAsset>()
                        .eq(CreativeAsset::getDeleted, 0)
                        .eq(req.getProjectId() != null, CreativeAsset::getProjectId, req.getProjectId())
                        .eq(req.getAssetType() != null, CreativeAsset::getAssetType, req.getAssetType())
                        .eq(req.getBizType() != null, CreativeAsset::getBizType, req.getBizType())
                        .orderByDesc(CreativeAsset::getCreateTime));
        return PageResult.of(req.getPageNo(), req.getPageSize(), page.getTotal(), page.getRecords().stream().map(AssetConvert::toPage).toList());
    }

    @Override
    public AssetDetailResp detail(Long assetId) {
        CreativeAsset asset = assetMapper.selectById(assetId);
        CreativeAsserts.notNull(asset, "asset not found");
        return AssetConvert.toDetail(asset);
    }

    @Override
    public void deleteAsset(Long assetId) {
        CreativeAsset asset = assetMapper.selectById(assetId);
        CreativeAsserts.notNull(asset, "asset not found");
        asset.setDeleted(1);
        assetMapper.updateById(asset);
    }
}
