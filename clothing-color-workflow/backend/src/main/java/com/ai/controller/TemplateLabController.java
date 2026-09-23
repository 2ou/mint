package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.TemplateLabProjectCreateRequest;
import com.ai.dto.TemplateLabProjectResponse;
import com.ai.dto.TemplateLabProjectUpdateRequest;
import com.ai.dto.TemplateLabTemplateCreateRequest;
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
    public ApiResponse<List<JsonNode>> templates(@RequestAttribute("userId") Long userId) {
        return ApiResponse.ok("查询成功", templateLabService.listTemplates(userId));
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

    @PostMapping(value = "/templates/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TemplateLabProjectResponse> parseTemplateImage(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("operator") String operator,
            @RequestAttribute("shopName") String shopName,
            @RequestParam("file") MultipartFile file,
            @RequestParam("canvasSpec") String canvasSpec,
            @RequestParam(value = "projectName", required = false) String projectName,
            @RequestParam(value = "notes", required = false) String notes) {
        return ApiResponse.ok("图片已解析为模板草稿", templateLabService.parseTemplateImage(
                file, canvasSpec, projectName, notes, userId, operator, shopName));
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

    @PostMapping("/projects/{id}/templates")
    public ApiResponse<JsonNode> createPersonalTemplate(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody TemplateLabTemplateCreateRequest request) {
        return ApiResponse.ok("个人模板已保存", templateLabService.createPersonalTemplate(id, userId, request));
    }

    @DeleteMapping("/templates/personal/{templateId}")
    public ApiResponse<Void> deletePersonalTemplate(
            @PathVariable("templateId") Long templateId,
            @RequestAttribute("userId") Long userId) {
        templateLabService.deletePersonalTemplate(templateId, userId);
        return ApiResponse.ok("个人模板已删除", null);
    }

    @PostMapping(value = "/projects/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadAsset(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("图片已上传", templateLabService.uploadAsset(id, userId, file));
    }

    @GetMapping("/projects/{id}/cutout-quote")
    public ApiResponse<Map<String, Object>> cutoutQuote(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId) {
        return ApiResponse.ok("查询成功", templateLabService.cutoutQuote(id, userId));
    }

    @PostMapping(value = "/projects/{id}/cutouts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> createCutout(
            @PathVariable("id") Long id,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("operator") String operator,
            @RequestAttribute("shopName") String shopName,
            @RequestParam("elementId") String elementId,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("抠图任务已提交", templateLabService.createCutout(
                id, userId, operator, shopName, elementId, sourceUrl, file));
    }

    @GetMapping("/projects/{id}/cutouts/{taskId}")
    public ApiResponse<Map<String, Object>> cutoutResult(
            @PathVariable("id") Long id,
            @PathVariable("taskId") String taskId,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("operator") String operator,
            @RequestAttribute("shopName") String shopName) {
        return ApiResponse.ok("查询成功", templateLabService.cutoutResult(
                id, userId, operator, shopName, taskId));
    }
}
