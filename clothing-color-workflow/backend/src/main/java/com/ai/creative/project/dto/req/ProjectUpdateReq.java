package com.ai.creative.project.dto.req;

import lombok.Data;

@Data
public class ProjectUpdateReq {
    private String projectName;
    private String description;
    private String coverUrl;
    private String remark;
}
