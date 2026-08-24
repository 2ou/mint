package com.ai.service;

import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusProjectResponse;
import com.ai.dto.AplusCopyUpdateRequest;
import org.springframework.data.domain.Page;

public interface AplusProjectService {
    AplusProjectResponse createProject(AplusProjectCreateRequest request, String operator, String shopName);
    AplusProjectResponse getProjectById(Long id);
    Page<AplusProjectResponse> getProjectPage(int page, int size, String spu, String status);
    AplusProjectResponse updateCopy(Long id, AplusCopyUpdateRequest request);
    AplusProjectResponse linkCanvas(Long id, Long canvasId);
    void deleteProject(Long id);
    void updateProjectStatus(Long id, String status, String errorMessage);
}
