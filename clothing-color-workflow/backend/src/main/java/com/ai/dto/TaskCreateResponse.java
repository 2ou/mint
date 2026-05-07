package com.ai.dto;

import com.ai.entity.ImageTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateResponse {

    private Long id;
    private String taskId;
    private String spu;
    private String status;
    private boolean success;
    private String prompt;
    private String model;
    private String resolution;
    private Integer taskType;
    private String shopName;
    private String operator;

    private String inputImageUrl;
    private String colorImageUrl;
    private String resultTempUrl;
    private String resultOssUrl;
    private String localPath;
    private LocalDateTime completeTime;
    private String errorMessage;


    private LocalDateTime createdAt;
    private BigDecimal cost;

    public TaskCreateResponse(ImageTask task) {
        this.id = task.getId();
        this.taskId = task.getTaskId();
        this.completeTime = task.getCompleteTime(); // 🔴 新增字段传给前端
        this.status = task.getStatus();
        this.success = "SUCCESS".equals(task.getStatus());
        this.inputImageUrl = task.getInputImageUrl();
        this.colorImageUrl = task.getColorImageUrl();
        this.resultTempUrl = task.getResultTempUrl();
        this.resultOssUrl = task.getResultOssUrl();
        this.localPath = task.getLocalPath();
        this.createdAt = task.getCreatedAt();
        this.prompt = task.getPrompt();
        this.spu = task.getSpu();
        this.model = task.getModel();
        this.resolution = task.getResolution();
        this.taskType = task.getTaskType();
        this.shopName = task.getShopName();
        this.operator = task.getOperator();
        this.errorMessage = task.getErrorMessage();
        this.cost = task.getCost();
    }
}