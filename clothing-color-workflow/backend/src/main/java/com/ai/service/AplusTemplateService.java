package com.ai.service;

import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusTemplateResponse;
import com.ai.dto.AplusTemplateSaveRequest;
import org.springframework.data.domain.Page;

public interface AplusTemplateService {
    AplusTemplateResponse saveTemplate(AplusTemplateSaveRequest request, String operator, String shopName);
    Page<AplusTemplateResponse> getTemplatePage(int page, int size, String templateName, String spu);
    AplusTemplateResponse getTemplateById(Long id);
    void deleteTemplate(Long id);
    AplusProjectCreateRequest applyTemplate(Long id);
}
