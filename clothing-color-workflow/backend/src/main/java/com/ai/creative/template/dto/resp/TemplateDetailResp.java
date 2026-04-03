package com.ai.creative.template.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateDetailResp {
    private Long id;
    private String templateCode;
    private String templateName;
    private String category;
    private String status;
    private String canvasJson;
    private String flowJson;
    private String configJson;
    private Integer useCount;
    private LocalDateTime updateTime;
}
