package com.ai.creative.project.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectVersionResp {
    private Long id;
    private Integer versionNo;
    private String saveType;
    private String summary;
    private Integer isCurrent;
    private LocalDateTime createTime;
}
