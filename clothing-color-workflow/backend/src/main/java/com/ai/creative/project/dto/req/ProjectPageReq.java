package com.ai.creative.project.dto.req;

import lombok.Data;

@Data
public class ProjectPageReq {
    private long pageNo = 1;
    private long pageSize = 10;
    private String projectName;
    private String status;
}
