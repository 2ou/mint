package com.ai.creative.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_asset")
public class CreativeAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String assetCode;
    private Long projectId;
    private Long projectVersionId;
    private String sourceType;
    private String assetType;
    private String bizType;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private Long durationMs;
    private String sourceUrl;
    private String ossUrl;
    private String thumbnailUrl;
    private String metadataJson;
    private Integer deleted;
    private String createBy;
    private LocalDateTime createTime;
}
