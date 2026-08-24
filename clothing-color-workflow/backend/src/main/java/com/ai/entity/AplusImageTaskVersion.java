package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Immutable snapshot kept before an A+ module is regenerated. */
@Data
@Entity
@Table(name = "aplus_image_task_version")
public class AplusImageTaskVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;
    private Long taskId;
    private Integer versionNumber;

    @Column(length = 10)
    private String moduleCode;

    @Column(length = 128)
    private String kieTaskId;

    @Column(columnDefinition = "text")
    private String prompt;

    @Column(columnDefinition = "text")
    private String referenceImagesJson;

    @Column(length = 20)
    private String aspectRatio;

    @Column(length = 20)
    private String resolution;

    @Column(length = 64)
    private String model;

    @Column(length = 20)
    private String status;

    @Column(length = 500)
    private String resultTempUrl;

    @Column(length = 500)
    private String resultOssUrl;

    @Column(columnDefinition = "text")
    private String qualityReportJson;

    @CreationTimestamp
    private LocalDateTime archivedAt;
}
