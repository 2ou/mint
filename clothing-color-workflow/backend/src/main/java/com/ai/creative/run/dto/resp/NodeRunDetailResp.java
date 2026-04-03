package com.ai.creative.run.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeRunDetailResp {
    private Long id;
    private String runCode;
    private Long projectId;
    private String nodeType;
    private String status;
    private Long asyncTaskId;
    private String outputJson;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
