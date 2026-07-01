package com.ai.dto;

import lombok.Data;

/**
 * 模特生成请求 DTO
 */
@Data
public class ModelGenerateRequest {
    /**
     * 模特类型：High Fashion/Commercial/Curve/Athletic/Natural/Mature/Street/Romantic/Minimalist/Glamour
     */
    private String modelType;

    /**
     * 族裔：Caucasian/African American/Latina/Asian American/Mixed
     */
    private String ethnicity;

    /**
     * 年龄范围：如 30-38
     */
    private String ageRange;

    /**
     * 特殊要求（用户自定义补充）
     */
    private String specialRequirements;

    /**
     * 模特名称（可选，不填则自动生成）
     */
    private String modelName;

    /**
     * 风格标签（可选）
     */
    private String styleTags;

    /**
     * 使用的文本模型：gpt（可选，默认 gpt）
     */
    private String textModel;

    // ========== 新增参数 ==========

    /**
     * 发型：long_wavy / short_bob / ponytail / braids / slicked_back 等
     */
    private String hairstyle;

    /**
     * 肤色：fair / medium / tan / dark / olive
     */
    private String skinTone;

    /**
     * 拍摄角度：front / side / three_quarter / back
     */
    private String cameraAngle;

    /**
     * 背景：white_studio / gray_studio / outdoor / urban
     */
    private String background;

    /**
     * 服装描述（可选，用于生成带服装的提示词）
     */
    private String clothingDescription;

    // ========== 2步流程新增参数 ==========

    /**
     * 服装图 OSS URL（选填，作为参考图传入生图模型）
     */
    private String clothingImageUrl;

    /**
     * 生图模型：nano-banana-pro / gpt-image-2-image-to-image
     */
    private String imageModel;

    /**
     * 分辨率：1K / 2K / 4K
     */
    private String resolution;

    /**
     * 画面比例：1:1 / 9:16 / 16:9 / 4:3 / 3:4 / auto
     */
    private String aspectRatio;

    /**
     * 生成数量：1-10，默认 1
     */
    private int batchCount = 1;

    /**
     * 名称前缀（选填，不填则自动生成）
     */
    private String namePrefix;
}
