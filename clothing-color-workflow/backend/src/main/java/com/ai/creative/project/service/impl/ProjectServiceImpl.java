package com.ai.creative.project.service.impl;

import com.ai.creative.common.CodeGenUtils;
import com.ai.creative.common.CreativeAsserts;
import com.ai.creative.common.PageResult;
import com.ai.creative.enums.ProjectLogActionEnum;
import com.ai.creative.enums.ProjectStatusEnum;
import com.ai.creative.enums.SaveTypeEnum;
import com.ai.creative.project.convert.ProjectConvert;
import com.ai.creative.project.dto.req.*;
import com.ai.creative.project.dto.resp.*;
import com.ai.creative.project.entity.CreativeProject;
import com.ai.creative.project.entity.CreativeProjectLog;
import com.ai.creative.project.entity.CreativeProjectVersion;
import com.ai.creative.project.mapper.CreativeProjectLogMapper;
import com.ai.creative.project.mapper.CreativeProjectMapper;
import com.ai.creative.project.mapper.CreativeProjectVersionMapper;
import com.ai.creative.project.service.ProjectService;
import com.ai.creative.template.entity.CreativeTemplate;
import com.ai.creative.template.mapper.CreativeTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private final CreativeProjectMapper projectMapper;
    private final CreativeProjectVersionMapper versionMapper;
    private final CreativeProjectLogMapper projectLogMapper;
    private final CreativeTemplateMapper templateMapper;

    @Override
    @Transactional
    public Long createProject(ProjectCreateReq req) {
        CreativeProject entity = new CreativeProject();
        entity.setProjectCode(CodeGenUtils.code("PRJ"));
        entity.setProjectName(req.getProjectName());
        entity.setProjectType("CANVAS");
        entity.setStatus(ProjectStatusEnum.DRAFT.name());
        entity.setSourceTemplateId(req.getSourceTemplateId());
        entity.setCurrentVersionNo(0);
        entity.setDescription(req.getDescription());
        entity.setCoverUrl(req.getCoverUrl());
        entity.setDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        if (req.getSourceTemplateId() != null) {
            CreativeTemplate template = templateMapper.selectById(req.getSourceTemplateId());
            if (template != null && template.getDeleted() == 0) {
                entity.setCurrentCanvasJson(template.getCanvasJson());
                entity.setCurrentFlowJson(template.getFlowJson());
                entity.setCurrentConfigJson(template.getConfigJson());
            }
        }

        projectMapper.insert(entity);
        log(entity.getId(), ProjectLogActionEnum.CREATE.name(), "create project");
        return entity.getId();
    }

    @Override
    public PageResult<ProjectPageResp> page(ProjectPageReq req) {
        Page<CreativeProject> page = new Page<>(req.getPageNo(), req.getPageSize());
        LambdaQueryWrapper<CreativeProject> qw = new LambdaQueryWrapper<CreativeProject>()
                .eq(CreativeProject::getDeleted, 0)
                .like(req.getProjectName() != null, CreativeProject::getProjectName, req.getProjectName())
                .eq(req.getStatus() != null, CreativeProject::getStatus, req.getStatus())
                .orderByDesc(CreativeProject::getUpdateTime);
        Page<CreativeProject> p = projectMapper.selectPage(page, qw);
        return PageResult.of(req.getPageNo(), req.getPageSize(), p.getTotal(), p.getRecords().stream().map(ProjectConvert::toPage).toList());
    }

    @Override
    public ProjectDetailResp detail(Long projectId) {
        return ProjectConvert.toDetail(getProject(projectId));
    }

    @Override
    public void updateProject(Long projectId, ProjectUpdateReq req) {
        CreativeProject p = getProject(projectId);
        BeanUtils.copyProperties(req, p);
        p.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(p);
    }

    @Override
    public void deleteProject(Long projectId) {
        CreativeProject p = getProject(projectId);
        p.setDeleted(1);
        p.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(p);
        log(projectId, ProjectLogActionEnum.DELETE.name(), "delete project");
    }

    @Override
    @Transactional
    public Long copyProject(Long projectId, ProjectCopyReq req) {
        CreativeProject src = getProject(projectId);
        ProjectCreateReq createReq = new ProjectCreateReq();
        createReq.setProjectName(req.getProjectName());
        createReq.setDescription(src.getDescription());
        createReq.setCoverUrl(src.getCoverUrl());
        Long newId = createProject(createReq);

        CreativeProject dst = getProject(newId);
        dst.setCurrentCanvasJson(src.getCurrentCanvasJson());
        dst.setCurrentFlowJson(src.getCurrentFlowJson());
        dst.setCurrentConfigJson(src.getCurrentConfigJson());
        dst.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(dst);

        log(newId, ProjectLogActionEnum.COPY.name(), "copy from project=" + projectId);
        return newId;
    }

    @Override
    public void autoSave(Long projectId, ProjectAutoSaveReq req) {
        CreativeProject p = getProject(projectId);
        p.setCurrentCanvasJson(req.getCanvasJson());
        p.setCurrentFlowJson(req.getFlowJson());
        p.setCurrentConfigJson(req.getConfigJson());
        p.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(p);
        log(projectId, ProjectLogActionEnum.AUTO_SAVE.name(), "auto save");
    }

    @Override
    @Transactional
    public Long manualSave(Long projectId, ProjectManualSaveReq req) {
        CreativeProject p = getProject(projectId);
        p.setCurrentCanvasJson(req.getCanvasJson());
        p.setCurrentFlowJson(req.getFlowJson());
        p.setCurrentConfigJson(req.getConfigJson());
        p.setStatus(ProjectStatusEnum.SAVED.name());
        p.setLastSaveTime(LocalDateTime.now());
        int nextNo = p.getCurrentVersionNo() == null ? 1 : p.getCurrentVersionNo() + 1;
        p.setCurrentVersionNo(nextNo);
        p.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(p);

        versionMapper.update(null, new LambdaUpdateWrapper<CreativeProjectVersion>()
                .eq(CreativeProjectVersion::getProjectId, projectId)
                .eq(CreativeProjectVersion::getDeleted, 0)
                .set(CreativeProjectVersion::getIsCurrent, 0));

        CreativeProjectVersion v = new CreativeProjectVersion();
        v.setProjectId(projectId);
        v.setVersionNo(nextNo);
        v.setSaveType(SaveTypeEnum.MANUAL.name());
        v.setCanvasJson(req.getCanvasJson());
        v.setFlowJson(req.getFlowJson());
        v.setConfigJson(req.getConfigJson());
        v.setSummary(req.getSummary());
        v.setIsCurrent(1);
        v.setDeleted(0);
        v.setCreateTime(LocalDateTime.now());
        versionMapper.insert(v);

        log(projectId, ProjectLogActionEnum.SAVE.name(), "manual save v" + nextNo);
        return v.getId();
    }

    @Override
    public List<ProjectVersionResp> listVersions(Long projectId) {
        return versionMapper.selectList(new LambdaQueryWrapper<CreativeProjectVersion>()
                        .eq(CreativeProjectVersion::getProjectId, projectId)
                        .eq(CreativeProjectVersion::getDeleted, 0)
                        .orderByDesc(CreativeProjectVersion::getVersionNo))
                .stream()
                .map(ProjectConvert::toVersion)
                .toList();
    }

    private CreativeProject getProject(Long projectId) {
        CreativeProject p = projectMapper.selectById(projectId);
        CreativeAsserts.notNull(p, "project not found");
        CreativeAsserts.isTrue(p.getDeleted() == 0, "project deleted");
        return p;
    }

    private void log(Long projectId, String action, String content) {
        CreativeProjectLog log = new CreativeProjectLog();
        log.setProjectId(projectId);
        log.setActionType(action);
        log.setContent(content);
        log.setOperator("system");
        log.setCreateTime(LocalDateTime.now());
        projectLogMapper.insert(log);
    }
}
