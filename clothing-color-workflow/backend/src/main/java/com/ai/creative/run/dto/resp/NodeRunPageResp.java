package com.ai.creative.run.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NodeRunPageResp {
    private Long id;
    private String runCode;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String status;
    private LocalDateTime createTime;
}
