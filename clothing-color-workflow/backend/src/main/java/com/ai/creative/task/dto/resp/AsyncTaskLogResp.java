package com.ai.creative.task.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsyncTaskLogResp {
    private Long id;
    private String logType;
    private String content;
    private LocalDateTime createTime;
}
