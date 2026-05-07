package com.ai.entity;

import com.ai.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "image_task")
public class ImageTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String spu;

    @Column(length = 128)
    private String taskId;

    @Column(length = 64)
    private String model;

    @Column(columnDefinition = "text")
    private String prompt;

    @Column(length = 32)
    private String resolution;

    @Column(length = 20)
    private String status;

    @Column(length = 500)
    private String inputImageUrl;

    @Column(length = 500)
    private String colorImageUrl;

    @Column(length = 500)
    private String resultTempUrl;

    @Column(length = 500)
    private String resultOssUrl;

    @Column(length = 500)
    private String localPath;

    @Column(columnDefinition = "text")
    private String errorMessage;

    // 🔴 任务类型 (1: 换色, 2: 场景)
    @Column
    private Integer taskType;

    @Column(length = 100)
    private String shopName;

    @Column(length = 50)
    private String operator;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime completeTime; // 🔴 新增：任务完成时间戳 (毫秒)

    // 🔴 新增：任务预估费用
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal cost;
}
