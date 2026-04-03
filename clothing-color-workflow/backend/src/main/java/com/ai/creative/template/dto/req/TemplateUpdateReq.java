package com.ai.creative.template.dto.req;

import lombok.Data;

@Data
public class TemplateUpdateReq {
    private String templateName;
    private String category;
    private String description;
    private String coverUrl;
    private String status;
}
