package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "model_library")
public class ModelLibrary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long identityId;

    @Column(length = 32)
    private String identityView;

    @Column
    private Integer variantIndex;

    @Column(columnDefinition = "TEXT")
    private String negativePrompt;

    @Column(length = 80)
    private String hairstyle;

    @Column(length = 40)
    private String skinTone;

    @Column(length = 40)
    private String cameraAngle;

    @Column(length = 80)
    private String background;

    @Column(columnDefinition = "TEXT")
    private String clothingDescription;

    @Column(length = 500)
    private String clothingImageUrl;

    @Column(length = 80)
    private String imageModel;

    @Column(length = 80)
    private String modelVersion;

    @Column(length = 20)
    private String resolution;

    @Column(length = 20)
    private String aspectRatio;

    private Long seed;

    @Column(columnDefinition = "TEXT")
    private String generationParamsJson;

    @Column(length = 32)
    private String storageStatus = "NOT_REQUESTED";

    @Column(length = 500)
    private String storageError;

    private Integer storageRetryCount = 0;

    private LocalDateTime nextStorageRetryAt;

    @Column(length = 100, nullable = false)
    private String modelName;  // 模特名称/编号

    @Column(length = 50, nullable = false)
    private String modelType;  // 模特类型：High Fashion/Commercial/Curve/Athletic/Natural/Mature/Street/Romantic/Minimalist/Glamour

    @Column(length = 50)
    private String ethnicity;  // 族裔：Caucasian/African American/Latina/Asian American/Mixed

    @Column(length = 20)
    private String ageRange;  // 年龄段：如 30-38

    @Column(length = 200)
    private String bodyType;  // 体型描述

    @Column(length = 500)
    private String styleTags;  // 风格标签，逗号分隔

    @Column(columnDefinition = "TEXT")
    private String promptTemplate;  // 提示词模板

    @Column(columnDefinition = "TEXT")
    private String generatedPrompt;  // 生成的完整提示词

    @Column(length = 500)
    private String coverImageUrl;  // 封面图 OSS 链接

    @Column(columnDefinition = "TEXT")
    private String sampleImages;  // 样本图 JSON 数组

    @Column(length = 20)
    private String status = "DRAFT";  // 状态：DRAFT/ACTIVE/DISABLED

    @Column
    private Integer usageCount = 0;  // 使用次数

    @Column(length = 128)
    private String taskId;  // KIE 任务 ID

    @Column(length = 20)
    private String taskStatus;  // 任务状态：CREATED/PROCESSING/SUCCESS/FAILED

    @Column(length = 500)
    private String resultUrl;  // 生成结果 URL

    @Column(length = 50)
    private String createdBy;  // 创建人

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
