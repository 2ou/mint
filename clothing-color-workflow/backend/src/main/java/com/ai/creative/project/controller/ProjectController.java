package com.ai.creative.project.controller;

import com.ai.creative.project.dto.req.*;
import com.ai.creative.project.dto.resp.*;
import com.ai.creative.project.service.ProjectService;
import com.ai.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/creative/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid ProjectCreateReq req){ return ApiResponse.ok("ok", projectService.createProject(req)); }
    @GetMapping("/page")
    public ApiResponse<?> page(ProjectPageReq req){ return ApiResponse.ok("ok", projectService.page(req)); }
    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDetailResp> detail(@PathVariable Long projectId){ return ApiResponse.ok("ok", projectService.detail(projectId)); }
    @PutMapping("/{projectId}")
    public ApiResponse<Void> update(@PathVariable Long projectId, @RequestBody ProjectUpdateReq req){ projectService.updateProject(projectId, req); return ApiResponse.ok("ok", null); }
    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable Long projectId){ projectService.deleteProject(projectId); return ApiResponse.ok("ok", null); }
    @PostMapping("/{projectId}/copy")
    public ApiResponse<Long> copy(@PathVariable Long projectId, @RequestBody @Valid ProjectCopyReq req){ return ApiResponse.ok("ok", projectService.copyProject(projectId, req)); }
    @PostMapping("/{projectId}/autosave")
    public ApiResponse<Void> autosave(@PathVariable Long projectId, @RequestBody @Valid ProjectAutoSaveReq req){ projectService.autoSave(projectId, req); return ApiResponse.ok("ok", null); }
    @PostMapping("/{projectId}/save")
    public ApiResponse<Long> save(@PathVariable Long projectId, @RequestBody @Valid ProjectManualSaveReq req){ return ApiResponse.ok("ok", projectService.manualSave(projectId, req)); }
    @GetMapping("/{projectId}/versions")
    public ApiResponse<List<ProjectVersionResp>> versions(@PathVariable Long projectId){ return ApiResponse.ok("ok", projectService.listVersions(projectId)); }
}
