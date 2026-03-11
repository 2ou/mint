package com.ai.dto;

import com.ai.entity.ImageTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateResponse {

    private Long id;
    private String taskId;
    private String status;
    private boolean success;

    private String inputImageUrl;
    private String colorImageUrl;
    private String resultTempUrl;
    private String resultOssUrl;
    private String localPath;

    public TaskCreateResponse(ImageTask task) {
        this.id = task.getId();
        this.taskId = task.getTaskId();
        this.status = task.getStatus();
        this.success = "SUCCESS".equals(task.getStatus());
        this.inputImageUrl = task.getInputImageUrl();
        this.colorImageUrl = task.getColorImageUrl();
        this.resultTempUrl = task.getResultTempUrl();
        this.resultOssUrl = task.getResultOssUrl();
        this.localPath = task.getLocalPath();
    }
}