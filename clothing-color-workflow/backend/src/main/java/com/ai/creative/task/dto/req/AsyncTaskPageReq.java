package com.ai.creative.task.dto.req;

import lombok.Data;

@Data
public class AsyncTaskPageReq {
    private long pageNo = 1;
    private long pageSize = 10;
    private Long projectId;
    private String status;
    private String taskType;
}
