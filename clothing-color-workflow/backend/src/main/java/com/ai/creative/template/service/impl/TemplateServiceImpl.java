package com.ai.creative.template.service.impl;

import com.ai.creative.common.CodeGenUtils;
import com.ai.creative.common.CreativeAsserts;
import com.ai.creative.common.PageResult;
import com.ai.creative.enums.ProjectLogActionEnum;
import com.ai.creative.enums.TemplateStatusEnum;
import com.ai.creative.project.dto.req.ProjectCreateReq;
import com.ai.creative.project.entity.CreativeProject;
import com.ai.creative.project.entity.CreativeProjectLog;
import com.ai.creative.project.entity.CreativeProjectVersion;
import com.ai.creative.project.mapper.CreativeProjectLogMapper;
import com.ai.creative.project.mapper.CreativeProjectMapper;
import com.ai.creative.project.mapper.CreativeProjectVersionMapper;
import com.ai.creative.project.service.ProjectService;
import com.ai.creative.template.convert.TemplateConvert;
import com.ai.creative.template.dto.req.*;
import com.ai.creative.template.dto.resp.*;
import com.ai.creative.template.entity.CreativeTemplate;
import com.ai.creative.template.mapper.CreativeTemplateMapper;
import com.ai.creative.template.service.TemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {
    private final CreativeTemplateMapper templateMapper;
    private final CreativeProjectMapper projectMapper;
    private final CreativeProjectVersionMapper versionMapper;
    private final ProjectService projectService;
    private final CreativeProjectLogMapper projectLogMapper;

    @Override
    public PageResult<TemplatePageResp> page(TemplatePageReq req) {
        Page<CreativeTemplate> page = templateMapper.selectPage(new Page<>(req.getPageNo(), req.getPageSize()),
                new LambdaQueryWrapper<CreativeTemplate>()
                        .eq(CreativeTemplate::getDeleted, 0)
                        .like(req.getTemplateName() != null, CreativeTemplate::getTemplateName, req.getTemplateName())
                        .eq(req.getStatus() != null, CreativeTemplate::getStatus, req.getStatus())
                        .eq(req.getCategory() != null, CreativeTemplate::getCategory, req.getCategory())
                        .orderByDesc(CreativeTemplate::getUpdateTime));
        return PageResult.of(req.getPageNo(), req.getPageSize(), page.getTotal(), page.getRecords().stream().map(TemplateConvert::toPage).toList());
    }

    @Override
    public TemplateDetailResp detail(Long templateId) {
        return TemplateConvert.toDetail(getTemplate(templateId));
    }

    @Override
    @Transactional
    public Long saveFromProject(Long projectId, TemplateSaveReq req) {
        CreativeProject project = projectMapper.selectById(projectId);
        CreativeAsserts.notNull(project, "project not found");

        String canvas = project.getCurrentCanvasJson();
        String flow = project.getCurrentFlowJson();
        String config = project.getCurrentConfigJson();
        if (isBlank(canvas)) {
            CreativeProjectVersion currentVersion = versionMapper.selectOne(new LambdaQueryWrapper<CreativeProjectVersion>()
                    .eq(CreativeProjectVersion::getProjectId, projectId)
                    .eq(CreativeProjectVersion::getDeleted, 0)
                    .eq(CreativeProjectVersion::getIsCurrent, 1)
                    .orderByDesc(CreativeProjectVersion::getVersionNo)
                    .last("limit 1"));
            if (currentVersion != null) {
                canvas = currentVersion.getCanvasJson();
                flow = currentVersion.getFlowJson();
                config = currentVersion.getConfigJson();
            }
        }

        CreativeTemplate t = new CreativeTemplate();
        t.setTemplateCode(CodeGenUtils.code("TPL"));
        t.setTemplateName(req.getTemplateName());
        t.setTemplateType("WORKFLOW");
        t.setCategory(req.getCategory());
        t.setSourceProjectId(projectId);
        t.setStatus(TemplateStatusEnum.ENABLED.name());
        t.setIsSystem(0);
        t.setCoverUrl(req.getCoverUrl());
        t.setDescription(req.getDescription());
        t.setCanvasJson(canvas);
        t.setFlowJson(flow);
        t.setConfigJson(config);
        t.setUseCount(0);
        t.setDeleted(0);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(t);

        CreativeProjectLog log = new CreativeProjectLog();
        log.setProjectId(projectId);
        log.setActionType(ProjectLogActionEnum.SAVE_AS_TEMPLATE.name());
        log.setContent("save as template:" + t.getId());
        log.setOperator("system");
        log.setCreateTime(LocalDateTime.now());
        projectLogMapper.insert(log);
        return t.getId();
    }

    @Override
    public void updateTemplate(Long templateId, TemplateUpdateReq req) {
        CreativeTemplate t = getTemplate(templateId);
        BeanUtils.copyProperties(req, t);
        t.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(t);
    }

    @Override
    public void deleteTemplate(Long templateId) {
        CreativeTemplate t = getTemplate(templateId);
        t.setDeleted(1);
        t.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(t);
    }

    @Override
    @Transactional
    public Long createProjectFromTemplate(Long templateId, TemplateCreateProjectReq req) {
        CreativeTemplate t = getTemplate(templateId);
        ProjectCreateReq createReq = new ProjectCreateReq();
        createReq.setProjectName(req.getProjectName());
        createReq.setDescription(req.getDescription());
        createReq.setSourceTemplateId(templateId);
        Long projectId = projectService.createProject(createReq);

        t.setUseCount((t.getUseCount() == null ? 0 : t.getUseCount()) + 1);
        t.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(t);
        return projectId;
    }

    private CreativeTemplate getTemplate(Long templateId) {
        CreativeTemplate t = templateMapper.selectById(templateId);
        CreativeAsserts.notNull(t, "template not found");
        CreativeAsserts.isTrue(t.getDeleted() == 0, "template deleted");
        return t;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
