package com.ai.creative.project.convert;

import com.ai.creative.project.dto.resp.ProjectDetailResp;
import com.ai.creative.project.dto.resp.ProjectPageResp;
import com.ai.creative.project.dto.resp.ProjectVersionResp;
import com.ai.creative.project.entity.CreativeProject;
import com.ai.creative.project.entity.CreativeProjectVersion;
import org.springframework.beans.BeanUtils;

public class ProjectConvert {
    public static ProjectDetailResp toDetail(CreativeProject entity) { ProjectDetailResp r = new ProjectDetailResp(); BeanUtils.copyProperties(entity, r); return r; }
    public static ProjectPageResp toPage(CreativeProject entity) { ProjectPageResp r = new ProjectPageResp(); BeanUtils.copyProperties(entity, r); return r; }
    public static ProjectVersionResp toVersion(CreativeProjectVersion entity) { ProjectVersionResp r = new ProjectVersionResp(); BeanUtils.copyProperties(entity, r); return r; }
}
