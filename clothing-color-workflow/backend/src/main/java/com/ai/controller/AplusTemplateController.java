package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusTemplateResponse;
import com.ai.dto.AplusTemplateSaveRequest;
import com.ai.service.AplusTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * A+ 套图模板 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/aplus/templates")
@RequiredArgsConstructor
@CrossOrigin
public class AplusTemplateController {

    private final AplusTemplateService templateService;

    /**
     * 1. 保存模板
     */
    @PostMapping
    public ApiResponse<AplusTemplateResponse> saveTemplate(
            @RequestBody AplusTemplateSaveRequest request,
            HttpServletRequest httpRequest) {
        String operator = (String) httpRequest.getAttribute("operator");
        String shopName = (String) httpRequest.getAttribute("shopName");
        AplusTemplateResponse response = templateService.saveTemplate(request, operator, shopName);
        return ApiResponse.ok("模板保存成功", response);
    }

    /**
     * 2. 获取模板列表（分页）
     */
    @GetMapping
    public ApiResponse<Page<AplusTemplateResponse>> getTemplatePage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "templateName", required = false) String templateName,
            @RequestParam(value = "spu", required = false) String spu) {
        Page<AplusTemplateResponse> result = templateService.getTemplatePage(page, size, templateName, spu);
        return ApiResponse.ok("ok", result);
    }

    /**
     * 3. 获取模板详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AplusTemplateResponse> getTemplate(@PathVariable Long id) {
        AplusTemplateResponse response = templateService.getTemplateById(id);
        return ApiResponse.ok("ok", response);
    }

    /**
     * 4. 删除模板
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ApiResponse.ok("模板删除成功", null);
    }

    /**
     * 5. 应用模板（返回可直接填入创建请求的数据）
     */
    @PostMapping("/{id}/apply")
    public ApiResponse<AplusProjectCreateRequest> applyTemplate(@PathVariable Long id) {
        AplusProjectCreateRequest request = templateService.applyTemplate(id);
        return ApiResponse.ok("模板应用成功", request);
    }
}
