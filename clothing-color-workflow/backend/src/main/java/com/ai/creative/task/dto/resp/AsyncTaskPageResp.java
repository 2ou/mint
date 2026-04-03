package com.ai.creative.task.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsyncTaskPageResp {
    private Long id;
    private String taskCode;
    private Long projectId;
    private String taskType;
    private String providerTaskId;
    private String status;
    private Integer retryCount;
    private LocalDateTime updateTime;
}
