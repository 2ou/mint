package com.ai.creative.template.dto.req;

import lombok.Data;

@Data
public class TemplatePageReq {
    private long pageNo = 1;
    private long pageSize = 10;
    private String templateName;
    private String status;
    private String category;
}
