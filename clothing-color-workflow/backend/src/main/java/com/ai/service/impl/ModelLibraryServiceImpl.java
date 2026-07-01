package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.dto.ModelCreateTaskRequest;
import com.ai.dto.ModelGenerateRequest;
import com.ai.entity.ModelLibrary;
import com.ai.exception.BusinessException;
import com.ai.repository.ModelLibraryRepository;
import com.ai.service.KieClientService;
import com.ai.service.ModelLibraryService;
import com.ai.service.OssService;
import com.ai.service.TextModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelLibraryServiceImpl implements ModelLibraryService {

    private final AppProperties appProperties;
    private final ModelLibraryRepository modelLibraryRepository;
    private final ModelPromptGenerator modelPromptGenerator;
    private final KieClientService kieClientService;
    private final TextModelService textModelService;
    private final OssService ossService;

    /**
     * 回调入口：通过 KIE taskId 查找任务并刷新状态
     */
    public boolean refreshTaskByKieTaskId(String kieTaskId) {
        ModelLibrary model = modelLibraryRepository.findByTaskId(kieTaskId);
        if (model == null) {
            return false;
        }
        try {
            KieTaskResult result = kieClientService.getFullResult(kieTaskId);
            if (result.isFinished()) {
                if (result.isSuccess()) {
                    model.setTaskStatus("SUCCESS");
                    model.setResultUrl(result.getResultUrl());
                    model.setCoverImageUrl(result.getResultUrl());
                } else {
                    model.setTaskStatus("FAILED");
                }
                modelLibraryRepository.save(model);
            }
            return true;
        } catch (Exception e) {
            log.error("[Model] callback refresh failed: taskId={}, error={}", kieTaskId, e.getMessage());
            return false;
        }
    }

    @Override
    public String generatePrompt(ModelGenerateRequest request) {
        return textModelService.generatePrompt(request, "gpt");
    }

    @Override
    @Transactional
    public ModelLibrary createModelTask(ModelCreateTaskRequest request) {
        // 1. 验证提示词
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new BusinessException("提示词不能为空");
        }

        // 2. 准备模型参数
        String model = request.getModel() != null ? request.getModel() : "nano-banana-pro";
        String resolution = request.getResolution() != null ? request.getResolution() : "2K";
        String aspectRatio = request.getAspectRatio() != null ? request.getAspectRatio() : "1:1";

        // 3. 调用 KIE API 创建任务
        String taskId = kieClientService.createTask(
            null, // spu 不需要
            request.getPrompt(),
            resolution,
            aspectRatio,
            model,
            null, // inputUrl 文生图不需要
            null, // colorUrl 不需要
            appProperties.getKie().getCallbackUrl()
        );

        // 4. 创建或更新模特记录
        ModelLibrary modelLibrary;
        if (request.getModelId() != null) {
            // 更新现有记录
            modelLibrary = modelLibraryRepository.findById(request.getModelId())
                .orElseThrow(() -> new BusinessException("模特记录不存在"));
        } else {
            // 创建新记录
            modelLibrary = new ModelLibrary();
            modelLibrary.setModelName(request.getModelName() != null ? request.getModelName() :
                "Model_" + System.currentTimeMillis());
            modelLibrary.setModelType(request.getModelType() != null ? request.getModelType() : "Commercial");
            modelLibrary.setEthnicity(request.getEthnicity());
            modelLibrary.setAgeRange(request.getAgeRange());
            modelLibrary.setBodyType(request.getBodyType());
            modelLibrary.setStyleTags(request.getStyleTags());
            modelLibrary.setCreatedBy("system"); // TODO: 从登录用户获取
        }

        // 5. 更新任务信息
        modelLibrary.setGeneratedPrompt(request.getPrompt());
        modelLibrary.setTaskId(taskId);
        modelLibrary.setTaskStatus("CREATED");
        modelLibrary.setStatus("DRAFT");

        // 6. 保存并返回
        return modelLibraryRepository.save(modelLibrary);
    }

    @Override
    @Transactional
    public List<ModelLibrary> batchCreateModelTasks(List<ModelCreateTaskRequest> requests) {
        List<ModelLibrary> results = new ArrayList<>();
        for (ModelCreateTaskRequest request : requests) {
            try {
                ModelLibrary model = createModelTask(request);
                results.add(model);
            } catch (Exception e) {
                log.error("批量创建任务失败: {}", e.getMessage(), e);
                // 创建一个失败记录
                ModelLibrary failedModel = new ModelLibrary();
                failedModel.setModelName(request.getModelName() != null ? request.getModelName() : "Failed_Model");
                failedModel.setModelType(request.getModelType() != null ? request.getModelType() : "Commercial");
                failedModel.setGeneratedPrompt(request.getPrompt());
                failedModel.setTaskStatus("FAILED");
                failedModel.setStatus("DRAFT");
                failedModel.setCreatedBy("system");
                results.add(modelLibraryRepository.save(failedModel));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public ModelLibrary saveModel(ModelLibrary model) {
        return modelLibraryRepository.save(model);
    }

    @Override
    @Transactional
    public ModelLibrary approveModel(Long id) {
        ModelLibrary model = modelLibraryRepository.findById(id)
            .orElseThrow(() -> new BusinessException("模特记录不存在"));

        if (!"SUCCESS".equals(model.getTaskStatus())) {
            throw new BusinessException("只有生成成功的模特才能审核通过");
        }

        // 入库时转存到永久桶
        if (model.getResultUrl() != null && !model.getResultUrl().isEmpty()) {
            try {
                String ossResult = ossService.uploadResultToOss(
                    "model-" + model.getId(), model.getResultUrl(), model.getId(), true);
                if (ossResult != null && ossResult.contains("|")) {
                    String[] parts = ossResult.split("\\|");
                    if (parts.length >= 1 && !parts[0].isEmpty()) {
                        model.setResultUrl(parts[0]);
                        model.setCoverImageUrl(parts[0]);
                        log.info("入库时已转存永久桶: {} -> {}", model.getId(), parts[0]);
                    }
                }
            } catch (Exception e) {
                log.error("入库转存永久桶失败（临时链接仍可用）: {} - {}", model.getId(), e.getMessage());
            }
        }

        model.setStatus("ACTIVE");
        return modelLibraryRepository.save(model);
    }

    @Override
    @Transactional
    public List<ModelLibrary> batchApproveModels(List<Long> ids) {
        List<ModelLibrary> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                ModelLibrary model = approveModel(id);
                results.add(model);
            } catch (Exception e) {
                log.error("批量审核失败: {} - {}", id, e.getMessage());
            }
        }
        return results;
    }

    @Override
    @Transactional
    public ModelLibrary disableModel(Long id) {
        ModelLibrary model = modelLibraryRepository.findById(id)
            .orElseThrow(() -> new BusinessException("模特记录不存在"));

        model.setStatus("DISABLED");
        return modelLibraryRepository.save(model);
    }

    @Override
    @Transactional
    public void deleteModel(Long id) {
        modelLibraryRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDeleteModels(List<Long> ids) {
        modelLibraryRepository.deleteAllById(ids);
    }

    @Override
    public ModelLibrary getModelById(Long id) {
        return modelLibraryRepository.findById(id)
            .orElseThrow(() -> new BusinessException("模特记录不存在"));
    }

    @Override
    public Page<ModelLibrary> searchModels(String type, String keyword, String status, int current, int size) {
        Pageable pageable = PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return modelLibraryRepository.searchModels(type, keyword, status, pageable);
    }

    @Override
    public Map<String, String> getModelTypes() {
        return modelPromptGenerator.getModelTypes();
    }

    @Override
    public Map<String, String> getFabricTypes() {
        return modelPromptGenerator.getAllFabricTypes();
    }

    @Override
    public Map<String, String> getLensRecommendation(String modelType) {
        return modelPromptGenerator.getLensRecommendation(modelType);
    }

    @Override
    @Scheduled(fixedDelay = 30000) // 每 30 秒刷新一次
    @Transactional
    public void refreshProcessingTasks() {
        List<ModelLibrary> processingTasks = modelLibraryRepository.findProcessingTasks();

        if (processingTasks.isEmpty()) {
            return;
        }

        log.info("刷新 {} 个处理中的模特生图任务", processingTasks.size());

        for (ModelLibrary model : processingTasks) {
            try {
                KieTaskResult result = kieClientService.getFullResult(model.getTaskId());

                if (result.isFinished()) {
                    if (result.isSuccess()) {
                        model.setTaskStatus("SUCCESS");
                        // 存临时链接
                        model.setResultUrl(result.getResultUrl());
                        model.setCoverImageUrl(result.getResultUrl());
                        log.info("模特生图任务完成: {}", model.getId());
                    } else {
                        model.setTaskStatus("FAILED");
                        log.warn("模特生图任务失败: {} - {}", model.getId(), result.getErrorMessage());
                    }
                    modelLibraryRepository.save(model);
                }
            } catch (Exception e) {
                log.error("刷新任务状态异常: {}", model.getId(), e);
            }
        }
    }

    @Override
    public List<ModelLibrary> getProcessingTasks() {
        return modelLibraryRepository.findProcessingTasks();
    }

    @Override
    public Map<String, Long> getModelTypeStats() {
        List<Object[]> stats = modelLibraryRepository.countByModelType();
        return stats.stream()
            .collect(Collectors.toMap(
                arr -> (String) arr[0],
                arr -> (Long) arr[1]
            ));
    }

    @Override
    public Page<ModelLibrary> getGenerationHistory(int current, int size) {
        Pageable pageable = PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return modelLibraryRepository.findAll(pageable);
    }

    @Override
    public List<ModelLibrary> getRecentModels(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return modelLibraryRepository.findAll(pageable).getContent();
    }

    @Override
    @Transactional
    public List<ModelLibrary> generateModels(ModelGenerateRequest request) {
        // 1. 调用文本模型生成提示词（在 Skill 约束下）
        String prompt = generatePrompt(request);

        // 2. 确定参数（带默认值）
        String imageModel = request.getImageModel() != null && !request.getImageModel().isEmpty()
                ? request.getImageModel() : "nano-banana-pro";
        String resolution = request.getResolution() != null && !request.getResolution().isEmpty()
                ? request.getResolution() : "2K";
        String aspectRatio = request.getAspectRatio() != null && !request.getAspectRatio().isEmpty()
                ? request.getAspectRatio() : "1:1";
        int count = request.getBatchCount() > 0 ? request.getBatchCount() : 1;
        String prefix = request.getNamePrefix() != null && !request.getNamePrefix().isEmpty()
                ? request.getNamePrefix() : "";

        // 3. 批量创建任务
        List<ModelLibrary> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                ModelLibrary model = new ModelLibrary();
                String modelName = prefix.isEmpty()
                        ? randomModelName()
                        : prefix + "_" + (i + 1);
                model.setModelName(modelName);
                model.setModelType(request.getModelType() != null ? request.getModelType() : "Commercial");
                model.setEthnicity(request.getEthnicity());
                model.setAgeRange(request.getAgeRange());
                model.setGeneratedPrompt(prompt);
                model.setCreatedBy("system");

                // 调用 KIE API 创建生图任务
                // createTask(spu, prompt, resolution, aspectRatio, model, inputUrl, colorUrl)
                String taskId = kieClientService.createTask(
                        null,                               // spu
                        prompt,                             // prompt
                        resolution,                         // resolution
                        aspectRatio,                        // aspectRatio
                        imageModel,                         // model
                        request.getClothingImageUrl(),      // inputUrl（服装参考图）
                        null,                               // colorUrl
                        appProperties.getKie().getCallbackUrl()
                );
                model.setTaskId(taskId);
                model.setTaskStatus("CREATED");
                model.setStatus("DRAFT");

                results.add(modelLibraryRepository.save(model));
            } catch (Exception e) {
                log.error("创建任务失败: {}", e.getMessage(), e);
                ModelLibrary failed = new ModelLibrary();
                failed.setModelName(randomModelName());
                failed.setModelType(request.getModelType() != null ? request.getModelType() : "Commercial");
                failed.setGeneratedPrompt(prompt);
                failed.setTaskStatus("FAILED");
                failed.setStatus("DRAFT");
                failed.setCreatedBy("system");
                results.add(modelLibraryRepository.save(failed));
            }
        }
        return results;
    }

    /**
     * 生成随机模特名称（不含时间戳，重名没关系）
     */
    private String randomModelName() {
        String[] prefixes = {"Aria","Bella","Chloe","Diana","Elena","Fiona","Grace","Hannah","Ivy","Jade","Kira","Luna","Mia","Nora","Olive","Piper","Quinn","Ruby","Stella","Tessa","Uma","Vera","Wren","Xena","Yara","Zara"};
        String[] suffixes = {"Studio","Look","Style","Vogue","Muse","Aura","Bloom","Charm","Dawn","Eve","Flair","Glow","Haze","Iris","Jewel","Kiss","Lush","Mist","Nyx","Opal"};
        String p = prefixes[new java.util.Random().nextInt(prefixes.length)];
        String s = suffixes[new java.util.Random().nextInt(suffixes.length)];
        return p + "_" + s;
    }
}
