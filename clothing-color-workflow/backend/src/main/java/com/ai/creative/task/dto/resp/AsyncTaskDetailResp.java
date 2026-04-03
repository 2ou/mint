package com.ai.creative.task.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsyncTaskDetailResp {
    private Long id;
    private String taskCode;
    private Long projectId;
    private Long nodeRunId;
    private String taskType;
    private String providerTaskId;
    private String status;
    private String resultUrl;
    private Long finalAssetId;
    private String failMsg;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
}
