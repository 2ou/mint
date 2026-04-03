package com.ai.creative.project.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectPageResp {
    private Long id;
    private String projectCode;
    private String projectName;
    private String status;
    private Integer currentVersionNo;
    private LocalDateTime lastRunTime;
    private LocalDateTime lastSaveTime;
    private LocalDateTime updateTime;
}
