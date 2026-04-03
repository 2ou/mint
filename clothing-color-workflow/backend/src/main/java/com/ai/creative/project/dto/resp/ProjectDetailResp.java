package com.ai.creative.project.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectDetailResp {
    private Long id;
    private String projectCode;
    private String projectName;
    private String status;
    private Integer currentVersionNo;
    private String currentCanvasJson;
    private String currentFlowJson;
    private String currentConfigJson;
    private String coverUrl;
    private String description;
    private LocalDateTime lastRunTime;
    private LocalDateTime lastSaveTime;
    private LocalDateTime updateTime;
}
