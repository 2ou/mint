package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A+ 模块图片生成任务
 */
@Data
@Entity
@Table(name = "aplus_image_task")
public class AplusImageTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联项目 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private AplusProject project;

    /** 模块编号：AD-01 ~ AD-07 */
    @Column(nullable = false, length = 10)
    private String moduleCode;

    /** 模块名称 */
    @Column(nullable = false, length = 50)
    private String moduleName;

    /** 模块文案（从 MD 文档解析） */
    @Column(columnDefinition = "text")
    private String moduleCopy;

    /** 模块补充参考图 URL（用户为该模块单独上传的补充照片） */
    @Column(columnDefinition = "text")
    private String supplementaryImageUrl;

    /** 模块补充文字说明（用户为该模块单独填写的补充信息） */
    @Column(columnDefinition = "text")
    private String supplementaryText;

    /** 生成的 Prompt */
    @Column(columnDefinition = "text")
    private String prompt;

    /** 图片比例：16:9 */
    @Column(length = 20)
    private String aspectRatio;

    /** KIE 平台任务 ID */
    @Column(length = 128)
    private String kieTaskId;

    /** 使用的模型 */
    @Column(length = 64)
    private String model;

    /** 任务状态：PENDING / PROCESSING / SUCCESS / FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    /** KIE 临时结果 URL */
    @Column(length = 500)
    private String resultTempUrl;

    /** OSS 永久 URL */
    @Column(length = 500)
    private String resultOssUrl;

    /** 错误信息 */
    @Column(columnDefinition = "text")
    private String errorMessage;

    /** 预估费用 */
    @Column(precision = 10, scale = 2)
    private BigDecimal cost;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
