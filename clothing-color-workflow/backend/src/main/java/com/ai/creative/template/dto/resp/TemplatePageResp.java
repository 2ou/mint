package com.ai.creative.template.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplatePageResp {
    private Long id;
    private String templateCode;
    private String templateName;
    private String category;
    private String status;
    private Integer useCount;
    private LocalDateTime updateTime;
}
