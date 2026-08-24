package com.ai.dto;

import com.ai.entity.AplusImageTask;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A+ 模块任务响应 DTO
 */
@Data
public class AplusImageTaskResponse {
    private Long id;
    private String moduleCode;
    private String moduleName;
    private String moduleCopy;
    private String supplementaryImageUrl;
    private String supplementaryText;
    private String referenceImagesJson;
    private String prompt;
    private Integer versionNumber;
    private String aspectRatio;
    private String resolution;
    private String kieTaskId;
    private String model;
    private String status;
    private String resultTempUrl;
    private String resultOssUrl;
    private String localPath;
    private String errorMessage;
    private String qualityStatus;
    private String qualityReportJson;
    private BigDecimal cost;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    public static AplusImageTaskResponse from(AplusImageTask task) {
        AplusImageTaskResponse resp = new AplusImageTaskResponse();
        resp.setId(task.getId());
        resp.setModuleCode(task.getModuleCode());
        resp.setModuleName(task.getModuleName());
        resp.setModuleCopy(task.getModuleCopy());
        resp.setSupplementaryImageUrl(task.getSupplementaryImageUrl());
        resp.setSupplementaryText(task.getSupplementaryText());
        resp.setReferenceImagesJson(task.getReferenceImagesJson());
        resp.setPrompt(task.getPrompt());
        resp.setVersionNumber(task.getVersionNumber());
        resp.setAspectRatio(task.getAspectRatio());
        resp.setResolution(task.getResolution());
        resp.setKieTaskId(task.getKieTaskId());
        resp.setModel(task.getModel());
        resp.setStatus(task.getStatus());
        resp.setResultTempUrl(task.getResultTempUrl());
        resp.setResultOssUrl(task.getResultOssUrl());
        resp.setLocalPath(task.getLocalPath());
        resp.setErrorMessage(task.getErrorMessage());
        resp.setQualityStatus(task.getQualityStatus());
        resp.setQualityReportJson(task.getQualityReportJson());
        resp.setCost(task.getCost());
        resp.setCreatedAt(task.getCreatedAt());
        resp.setCompletedAt(task.getCompletedAt());
        return resp;
    }
}
