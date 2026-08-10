package com.ai.dto;

import lombok.Data;

/**
 * 模特生图任务请求 DTO
 */
@Data
public class ModelCreateTaskRequest {
    /**
     * 模特库 ID（可选，如果已有则更新，没有则创建）
     */
    private Long modelId;

    /**
     * 完整的提示词
     */
    private String prompt;

    /**
     * AI 模型：nano-banana-pro / gpt-image-2-image-to-image
     */
    private String model;

    /**
     * 分辨率：1K/2K/4K
     */
    private String resolution;

    /**
     * 画面比例：auto/1:1/9:16/16:9/4:3/3:4
     */
    private String aspectRatio;

    /**
     * 模特名称（新建时必填）
     */
    private String modelName;

    /**
     * 模特类型（新建时必填）
     */
    private String modelType;

    /**
     * 族裔（新建时可选）
     */
    private String ethnicity;

    /**
     * 年龄范围（新建时可选）
     */
    private String ageRange;

    /**
     * 体型描述（新建时可选）
     */
    private String bodyType;

    /**
     * 风格标签（新建时可选）
     */
    private String styleTags;

    private Long identityId;

    private String identityView;

    private String hairstyle;

    private String skinTone;

    private String cameraAngle;

    private String background;

    private String clothingDescription;

    private String clothingImageUrl;

    private String negativePrompt;

    private Long seed;
}
