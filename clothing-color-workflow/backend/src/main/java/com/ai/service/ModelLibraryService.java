package com.ai.service;

import com.ai.dto.ModelCreateTaskRequest;
import com.ai.dto.ModelGenerateRequest;
import com.ai.entity.ModelLibrary;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface ModelLibraryService {

    /**
     * 生成提示词
     */
    String generatePrompt(ModelGenerateRequest request);

    /**
     * 创建生图任务
     */
    ModelLibrary createModelTask(ModelCreateTaskRequest request);

    /**
     * 批量创建生图任务
     */
    List<ModelLibrary> batchCreateModelTasks(List<ModelCreateTaskRequest> requests);

    /**
     * 保存/更新模特
     */
    ModelLibrary saveModel(ModelLibrary model);

    /**
     * 审核通过
     */
    ModelLibrary approveModel(Long id);

    /**
     * 批量审核通过
     */
    List<ModelLibrary> batchApproveModels(List<Long> ids);

    /**
     * 停用模特
     */
    ModelLibrary disableModel(Long id);

    /**
     * 删除模特
     */
    void deleteModel(Long id);

    /**
     * 批量删除模特
     */
    void batchDeleteModels(List<Long> ids);

    /**
     * 根据 ID 获取模特
     */
    ModelLibrary getModelById(Long id);

    /**
     * 分页搜索模特库
     */
    Page<ModelLibrary> searchModels(String type, String keyword, String status, int current, int size);

    /**
     * 获取所有模特类型
     */
    Map<String, String> getModelTypes();

    /**
     * 获取面料类型
     */
    Map<String, String> getFabricTypes();

    /**
     * 获取镜头推荐
     */
    Map<String, String> getLensRecommendation(String modelType);

    /**
     * 刷新处理中的任务状态
     */
    void refreshProcessingTasks();

    /**
     * 获取处理中的任务列表
     */
    List<ModelLibrary> getProcessingTasks();

    /**
     * 统计各类型模特数量
     */
    Map<String, Long> getModelTypeStats();

    /**
     * 获取生成历史记录
     */
    Page<ModelLibrary> getGenerationHistory(int current, int size);

    /**
     * 获取最近生成的模特
     */
    List<ModelLibrary> getRecentModels(int limit);

    /**
     * 统一生成流程：文本模型生提示词 → 生图模型创建任务
     * @param request 生成请求（包含所有参数）
     * @return 创建的任务列表
     */
    List<ModelLibrary> generateModels(ModelGenerateRequest request);
}
