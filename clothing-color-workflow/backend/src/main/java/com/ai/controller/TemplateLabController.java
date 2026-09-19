package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.TemplateLabProjectCreateRequest;
import com.ai.dto.TemplateLabProjectResponse;
import com.ai.dto.TemplateLabProjectUpdateRequest;
import com.ai.service.TemplateLabService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/template-lab")
@RequiredArgsConstructor
public class TemplateLabController {

    private final TemplateLabService templateLabService;

    @GetMapping("/templates")
    public ApiResponse<List<JsonNode>> templates() {
        return ApiResponse.ok("查询成功", templateLabService.listTemplates());
    }

    @GetMapping("/projects")
    public ApiResponse<List<TemplateLabProjectResponse>> projects(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok("查询成功", templateLabService.listProjects(userId));
    }

    @PostMapping("/projects")
    public ApiResponse<TemplateLabProjectResponse> createProject(
            @Valid @RequestBody TemplateLabProjectCreateRequest request,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("operator") String operator,
            @RequestAttribute("shopName") String shopName) {
        return ApiResponse.ok("项目已创建", templateLabService.createProject(request, userId, operator, shopName));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<TemplateLabProjectResponse> project(@PathVariable("id") Long id,
                                                           @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok("查询成功", templateLabService.getProject(id, userId));
    }

    @PutMapping("/projects/{id}")
    public ApiResponse<TemplateLabProjectResponse> updateProject(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody TemplateLabProjectUpdateRequest request) {
        return ApiResponse.ok("项目已保存", templateLabService.updateProject(id, userId, request));
    }

    @PostMapping("/projects/{id}/duplicate")
    public ApiResponse<TemplateLabProjectResponse> duplicateProject(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok("项目已复制", templateLabService.duplicateProject(id, userId));
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable("id") Long id,
                                           @RequestAttribute("userId") Long userId) {
        templateLabService.deleteProject(id, userId);
        return ApiResponse.ok("项目已删除", null);
    }

    @PostMapping(value = "/projects/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadAsset(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("图片已上传", templateLabService.uploadAsset(id, userId, file));
    }
}
