package com.ai.creative.template.controller;

import com.ai.creative.template.dto.req.*;
import com.ai.creative.template.dto.resp.*;
import com.ai.creative.template.service.TemplateService;
import com.ai.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/creative/templates")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;

    @GetMapping("/page")
    public ApiResponse<?> page(TemplatePageReq req){ return ApiResponse.ok("ok", templateService.page(req)); }
    @GetMapping("/{templateId}")
    public ApiResponse<TemplateDetailResp> detail(@PathVariable Long templateId){ return ApiResponse.ok("ok", templateService.detail(templateId)); }
    @PostMapping("/from-project/{projectId}")
    public ApiResponse<Long> saveFromProject(@PathVariable Long projectId, @RequestBody @Valid TemplateSaveReq req){ return ApiResponse.ok("ok", templateService.saveFromProject(projectId, req)); }
    @PutMapping("/{templateId}")
    public ApiResponse<Void> update(@PathVariable Long templateId, @RequestBody TemplateUpdateReq req){ templateService.updateTemplate(templateId, req); return ApiResponse.ok("ok", null); }
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(@PathVariable Long templateId){ templateService.deleteTemplate(templateId); return ApiResponse.ok("ok", null); }
    @PostMapping("/{templateId}/create-project")
    public ApiResponse<Long> createProject(@PathVariable Long templateId, @RequestBody @Valid TemplateCreateProjectReq req){ return ApiResponse.ok("ok", templateService.createProjectFromTemplate(templateId, req)); }
}
