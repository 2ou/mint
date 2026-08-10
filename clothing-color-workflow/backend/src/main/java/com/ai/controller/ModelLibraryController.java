package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.ModelCreateTaskRequest;
import com.ai.dto.ModelGenerateRequest;
import com.ai.entity.ModelLibrary;
import com.ai.entity.ModelIdentity;
import com.ai.service.ModelLibraryService;
import com.ai.service.impl.ModelPromptGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@CrossOrigin
public class ModelLibraryController {

    private final ModelLibraryService modelLibraryService;
    private final ModelPromptGenerator modelPromptGenerator;

    /**
     * 生成提示词
     */
    @PostMapping("/generate-prompt")
    public ApiResponse<String> generatePrompt(@RequestBody ModelGenerateRequest request) {
        String prompt = modelLibraryService.generatePrompt(request);
        return ApiResponse.ok("提示词生成成功", prompt);
    }

    /**
     * 创建生图任务
     */
    @PostMapping("/create-task")
    public ApiResponse<ModelLibrary> createModelTask(@RequestBody ModelCreateTaskRequest request) {
        ModelLibrary model = modelLibraryService.createModelTask(request);
        return ApiResponse.ok("生图任务创建成功", model);
    }

    /**
     * 批量创建生图任务
     */
    @PostMapping("/batch-create-tasks")
    public ApiResponse<List<ModelLibrary>> batchCreateModelTasks(@RequestBody List<ModelCreateTaskRequest> requests) {
        List<ModelLibrary> models = modelLibraryService.batchCreateModelTasks(requests);
        return ApiResponse.ok("批量任务创建成功，共 " + models.size() + " 个", models);
    }

    /**
     * 保存/更新模特
     */
    @PostMapping("/save")
    public ApiResponse<ModelLibrary> saveModel(@RequestBody ModelLibrary model) {
        return ApiResponse.ok("保存成功", modelLibraryService.saveModel(model));
    }

    /**
     * 审核通过
     */
    @PutMapping("/{id}/approve")
    public ApiResponse<ModelLibrary> approveModel(@PathVariable("id") Long id) {
        return ApiResponse.ok("审核通过", modelLibraryService.approveModel(id));
    }

    /**
     * 批量审核通过
     */
    @PostMapping("/batch-approve")
    public ApiResponse<List<ModelLibrary>> batchApproveModels(@RequestBody List<Long> ids) {
        List<ModelLibrary> models = modelLibraryService.batchApproveModels(ids);
        return ApiResponse.ok("批量审核成功，共 " + models.size() + " 个", models);
    }

    /**
     * 停用模特
     */
    @PutMapping("/{id}/disable")
    public ApiResponse<ModelLibrary> disableModel(@PathVariable("id") Long id) {
        return ApiResponse.ok("已停用", modelLibraryService.disableModel(id));
    }

    /**
     * 删除模特
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable("id") Long id) {
        modelLibraryService.deleteModel(id);
        return ApiResponse.ok("删除成功", null);
    }

    /**
     * 批量删除模特
     */
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDeleteModels(@RequestBody List<Long> ids) {
        modelLibraryService.batchDeleteModels(ids);
        return ApiResponse.ok("批量删除成功，共 " + ids.size() + " 个", null);
    }

    /**
     * 根据 ID 获取模特
     */
    @GetMapping("/{id}")
    public ApiResponse<ModelLibrary> getModelById(@PathVariable("id") Long id) {
        return ApiResponse.ok("ok", modelLibraryService.getModelById(id));
    }

    /**
     * 分页搜索模特库
     */
    @GetMapping("/page")
    public ApiResponse<Page<ModelLibrary>> page(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {

        Page<ModelLibrary> pageResult = modelLibraryService.searchModels(type, keyword, status, current, size);
        return ApiResponse.ok("ok", pageResult);
    }

    /**
     * 获取所有模特类型
     */
    @GetMapping("/types")
    public ApiResponse<Map<String, String>> getModelTypes() {
        return ApiResponse.ok("ok", modelLibraryService.getModelTypes());
    }

    /**
     * 获取所有面料类型
     */
    @GetMapping("/fabrics")
    public ApiResponse<Map<String, String>> getFabricTypes() {
        return ApiResponse.ok("ok", modelLibraryService.getFabricTypes());
    }

    /**
     * 获取镜头推荐
     */
    @GetMapping("/lens-recommendation/{modelType}")
    public ApiResponse<Map<String, String>> getLensRecommendation(@PathVariable("modelType") String modelType) {
        return ApiResponse.ok("ok", modelLibraryService.getLensRecommendation(modelType));
    }

    /**
     * 获取处理中的任务列表
     */
    @GetMapping("/processing")
    public ApiResponse<List<ModelLibrary>> getProcessingTasks() {
        return ApiResponse.ok("ok", modelLibraryService.getProcessingTasks());
    }

    @GetMapping("/task-completion-mode")
    public ApiResponse<Map<String, String>> getTaskCompletionMode() {
        return ApiResponse.ok("ok", Map.of("mode", modelLibraryService.getTaskCompletionMode()));
    }

    @GetMapping("/identities/active")
    public ApiResponse<List<ModelIdentity>> getActiveIdentities() {
        return ApiResponse.ok("ok", modelLibraryService.getActiveIdentities());
    }

    @GetMapping("/identities/{id}")
    public ApiResponse<ModelIdentity> getIdentity(@PathVariable("id") Long id) {
        return ApiResponse.ok("ok", modelLibraryService.getIdentityById(id));
    }

    @GetMapping("/identities/{id}/assets")
    public ApiResponse<List<ModelLibrary>> getIdentityAssets(@PathVariable("id") Long id) {
        return ApiResponse.ok("ok", modelLibraryService.getIdentityAssets(id));
    }

    @PostMapping("/identities/{id}/activate")
    public ApiResponse<ModelIdentity> activateIdentity(@PathVariable("id") Long id) {
        return ApiResponse.ok("Identity package activated", modelLibraryService.activateIdentity(id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<ModelLibrary> retryTask(@PathVariable("id") Long id) {
        return ApiResponse.ok("Task retried", modelLibraryService.retryTask(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ModelLibrary> cancelTask(@PathVariable("id") Long id) {
        return ApiResponse.ok("Task canceled locally", modelLibraryService.cancelTask(id));
    }

    /**
     * 手动刷新任务状态
     */
    @PostMapping("/refresh-tasks")
    public ApiResponse<Void> refreshTasks() {
        modelLibraryService.refreshProcessingTasks();
        return ApiResponse.ok("刷新成功", null);
    }

    /**
     * 获取模特类型统计
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> getModelTypeStats() {
        return ApiResponse.ok("ok", modelLibraryService.getModelTypeStats());
    }

    /**
     * 获取生成历史记录
     */
    @GetMapping("/history")
    public ApiResponse<Page<ModelLibrary>> getGenerationHistory(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<ModelLibrary> history = modelLibraryService.getGenerationHistory(current, size);
        return ApiResponse.ok("ok", history);
    }

    /**
     * 获取最近生成的模特
     */
    @GetMapping("/recent")
    public ApiResponse<List<ModelLibrary>> getRecentModels(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<ModelLibrary> recentModels = modelLibraryService.getRecentModels(limit);
        return ApiResponse.ok("ok", recentModels);
    }

    /**
     * 热更新配置（重新加载配置文件）
     */
    @PostMapping("/reload-config")
    public ApiResponse<String> reloadConfig() {
        modelPromptGenerator.reloadConfig();
        return ApiResponse.ok("配置已重新加载", null);
    }

    /**
     * 统一生成流程：参数提交 → 文本模型生提示词 → 生图模型创建任务 → 返回任务列表
     */
    @PostMapping("/generate")
    public ApiResponse<List<ModelLibrary>> generateModels(@RequestBody ModelGenerateRequest request) {
        List<ModelLibrary> results = modelLibraryService.generateModels(request);
        return ApiResponse.ok("生成任务已创建，共 " + results.size() + " 个", results);
    }
}
