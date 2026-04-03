package com.ai.creative.project.service;

import com.ai.creative.common.PageResult;
import com.ai.creative.project.dto.req.*;
import com.ai.creative.project.dto.resp.*;

import java.util.List;

public interface ProjectService {
    Long createProject(ProjectCreateReq req);
    PageResult<ProjectPageResp> page(ProjectPageReq req);
    ProjectDetailResp detail(Long projectId);
    void updateProject(Long projectId, ProjectUpdateReq req);
    void deleteProject(Long projectId);
    Long copyProject(Long projectId, ProjectCopyReq req);
    void autoSave(Long projectId, ProjectAutoSaveReq req);
    Long manualSave(Long projectId, ProjectManualSaveReq req);
    List<ProjectVersionResp> listVersions(Long projectId);
}
