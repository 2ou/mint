package com.ai.creative.template.service;

import com.ai.creative.common.PageResult;
import com.ai.creative.template.dto.req.*;
import com.ai.creative.template.dto.resp.*;

public interface TemplateService {
    PageResult<TemplatePageResp> page(TemplatePageReq req);
    TemplateDetailResp detail(Long templateId);
    Long saveFromProject(Long projectId, TemplateSaveReq req);
    void updateTemplate(Long templateId, TemplateUpdateReq req);
    void deleteTemplate(Long templateId);
    Long createProjectFromTemplate(Long templateId, TemplateCreateProjectReq req);
}
