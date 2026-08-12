package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.dto.ModelCreateTaskRequest;
import com.ai.dto.ModelGenerateRequest;
import com.ai.dto.ModelIdentityContext;
import com.ai.entity.ModelIdentity;
import com.ai.entity.ModelLibrary;
import com.ai.exception.BusinessException;
import com.ai.repository.ModelIdentityRepository;
import com.ai.repository.ModelLibraryRepository;
import com.ai.service.KieClientService;
import com.ai.service.ModelLibraryService;
import com.ai.service.OssService;
import com.ai.service.TextModelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelLibraryServiceImpl implements ModelLibraryService {

    private static final int MAX_PROCESSING_TASKS = 10;
    private static final int MAX_STORAGE_RETRIES = 8;
    private static final List<String> DEFAULT_IDENTITY_VIEWS = List.of("FRONT", "SIDE", "BACK", "EXPRESSION");
    private static final List<String> PROCESSING_STATUSES = List.of("CREATED", "PROCESSING");
    private static final String DEFAULT_NEGATIVE_PROMPT =
            "different person, inconsistent face, inconsistent hairstyle, changed skin tone, changed body shape, " +
            "extra limbs, malformed hands, text, watermark, logo, plastic skin, airbrushed skin, blurry, cropped head";

    private final AppProperties appProperties;
    private final ModelLibraryRepository modelLibraryRepository;
    private final ModelIdentityRepository modelIdentityRepository;
    private final ModelPromptGenerator modelPromptGenerator;
    private final KieClientService kieClientService;
    private final TextModelService textModelService;
    private final OssService ossService;
    private final ObjectMapper objectMapper;

    @Override
    public String generatePrompt(ModelGenerateRequest request) {
        return textModelService.generatePrompt(request, KieGptModels.normalizeTextModel(request.getTextModel()));
    }

    @Override
    @Transactional
    public ModelLibrary createModelTask(ModelCreateTaskRequest request) {
        requirePrompt(request.getPrompt());
        ensureTaskCapacity(1);

        ModelLibrary model = request.getModelId() == null
                ? new ModelLibrary()
                : getModelById(request.getModelId());
        if (request.getIdentityId() != null) {
            getIdentityById(request.getIdentityId());
        }

        populateModelRecord(model, request, request.getPrompt(), request.getIdentityId(),
                defaultIfBlank(request.getIdentityView(), "REFERENCE"), 1);
        submitModelTask(model);
        return modelLibraryRepository.save(model);
    }

    @Override
    @Transactional
    public List<ModelLibrary> batchCreateModelTasks(List<ModelCreateTaskRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        ensureTaskCapacity(requests.size());
        List<ModelLibrary> results = new ArrayList<>();
        for (ModelCreateTaskRequest request : requests) {
            try {
                results.add(createModelTaskWithoutCapacityCheck(request));
            } catch (Exception e) {
                results.add(saveFailedRecord(request, request.getPrompt(), request.getIdentityId(), request.getIdentityView(), 1, e));
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
        ModelLibrary model = getModelById(id);
        if (!"SUCCESS".equals(model.getTaskStatus())) {
            throw new BusinessException("Only successful model tasks can be added to the library");
        }

        ModelLibrary stored = persistResult(model);
        if ("RETRY_PENDING".equals(stored.getStorageStatus())) {
            throw new BusinessException("Permanent storage is pending repair. The task will retry automatically.");
        }
        activateIdentityIfReady(stored.getIdentityId());
        return stored;
    }

    @Override
    @Transactional
    public List<ModelLibrary> batchApproveModels(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ModelLibrary> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                results.add(approveModel(id));
            } catch (Exception e) {
                log.warn("[Model] approve skipped: id={}, reason={}", id, e.getMessage());
            }
        }
        return results;
    }

    @Override
    @Transactional
    public ModelLibrary disableModel(Long id) {
        ModelLibrary model = getModelById(id);
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
        if (ids != null && !ids.isEmpty()) {
            modelLibraryRepository.deleteAllById(ids);
        }
    }

    @Override
    public ModelLibrary getModelById(Long id) {
        return modelLibraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Model record not found"));
    }

    @Override
    public Page<ModelLibrary> searchModels(String type, String keyword, String status, int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));
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
    @Transactional
    public boolean refreshTaskByKieTaskId(String kieTaskId) {
        ModelLibrary model = modelLibraryRepository.findByTaskId(kieTaskId);
        if (model == null || "CANCELED".equals(model.getTaskStatus())) {
            return model != null;
        }
        try {
            applyTaskResult(model, kieClientService.getFullResult(kieTaskId));
            return true;
        } catch (Exception e) {
            log.error("[Model] callback refresh failed: taskId={}, error={}", kieTaskId, e.getMessage());
            return false;
        }
    }

    @Override
    public void refreshProcessingTasks() {
        refreshProcessingTasksInternal("manual");
    }

    @Scheduled(fixedDelay = 30000)
    public void scheduledPollingRefresh() {
        if (!isCallbackCompletionEnabled()) {
            refreshProcessingTasksInternal("polling");
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void reconcileCallbackTasks() {
        if (isCallbackCompletionEnabled()) {
            refreshProcessingTasksInternal("callback-reconciliation");
        }
    }

    @Override
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void retryPendingStorage() {
        List<ModelLibrary> candidates = modelLibraryRepository.findStorageRetryCandidates(LocalDateTime.now());
        for (ModelLibrary model : candidates) {
            if (safeInt(model.getStorageRetryCount()) >= MAX_STORAGE_RETRIES) {
                continue;
            }
            persistResult(model);
            activateIdentityIfReady(model.getIdentityId());
        }
    }

    @Override
    public List<ModelLibrary> getProcessingTasks() {
        return modelLibraryRepository.findProcessingTasks();
    }

    @Override
    public Map<String, Long> getModelTypeStats() {
        return modelLibraryRepository.countByModelType().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    @Override
    public Page<ModelLibrary> getGenerationHistory(int current, int size) {
        Pageable pageable = PageRequest.of(Math.max(current - 1, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return modelLibraryRepository.findAll(pageable);
    }

    @Override
    public List<ModelLibrary> getRecentModels(int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "createdAt"));
        return modelLibraryRepository.findAll(pageable).getContent();
    }

    @Override
    @Transactional
    public List<ModelLibrary> generateModels(ModelGenerateRequest request) {
        List<String> views = normalizeViews(request.getIdentityViews(), request.getCameraAngle());
        int variantsPerView = Math.max(1, request.getBatchCount());
        int taskCount = views.size() * variantsPerView;
        if (taskCount > MAX_PROCESSING_TASKS) {
            throw new BusinessException("An identity package can create at most 10 tasks at a time");
        }
        ensureTaskCapacity(taskCount);

        String identityPrompt = generatePrompt(request);
        String identityName = defaultIfBlank(request.getNamePrefix(), randomModelName());
        String negativePrompt = defaultIfBlank(request.getNegativePrompt(), DEFAULT_NEGATIVE_PROMPT);
        String imageModel = defaultIfBlank(request.getImageModel(), "nano-banana-pro");
        Long seed = request.getSeed() == null
                ? ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
                : request.getSeed();

        ModelIdentity identity = new ModelIdentity();
        identity.setIdentityCode("MID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        identity.setIdentityName(identityName);
        identity.setModelType(defaultIfBlank(request.getModelType(), "Commercial"));
        identity.setEthnicity(request.getEthnicity());
        identity.setAgeRange(request.getAgeRange());
        identity.setHairstyle(request.getHairstyle());
        identity.setSkinTone(request.getSkinTone());
        identity.setStyleTags(request.getStyleTags());
        identity.setIdentityPrompt(identityPrompt);
        identity.setNegativePrompt(negativePrompt);
        identity.setImageModel(imageModel);
        identity.setModelVersion(imageModel);
        identity.setSeed(seed);
        identity.setRequiredViews(writeJson(views));
        identity.setStatus("DRAFT");
        identity.setStorageStatus("PENDING");
        identity.setCreatedBy("system");
        identity = modelIdentityRepository.save(identity);

        List<ModelLibrary> results = new ArrayList<>();
        for (String view : views) {
            for (int variantIndex = 1; variantIndex <= variantsPerView; variantIndex++) {
                String taskPrompt = buildIdentityViewPrompt(identityPrompt, view, variantIndex, negativePrompt);
                try {
                    ModelLibrary model = createIdentityAsset(request, identity, view, variantIndex, taskPrompt, seed);
                    results.add(model);
                } catch (Exception e) {
                    results.add(saveFailedIdentityAsset(request, identity, view, variantIndex, taskPrompt, seed, e));
                }
            }
        }
        return results;
    }

    @Override
    public List<ModelIdentity> getActiveIdentities() {
        return modelIdentityRepository.findByStatusOrderByUpdatedAtDesc("ACTIVE");
    }

    @Override
    public ModelIdentity getIdentityById(Long id) {
        return modelIdentityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Model identity not found"));
    }

    @Override
    public List<ModelLibrary> getIdentityAssets(Long identityId) {
        getIdentityById(identityId);
        return modelLibraryRepository.findByIdentityIdOrderByCreatedAtAsc(identityId);
    }

    @Override
    @Transactional
    public ModelIdentity activateIdentity(Long identityId) {
        ModelIdentity identity = getIdentityById(identityId);
        List<ModelLibrary> assets = modelLibraryRepository.findByIdentityIdOrderByCreatedAtAsc(identityId);
        if (assets.isEmpty()) {
            throw new BusinessException("The identity package has no generated assets");
        }
        for (ModelLibrary asset : assets) {
            if ("SUCCESS".equals(asset.getTaskStatus()) && !"PERSISTED".equals(asset.getStorageStatus())) {
                persistResult(asset);
            }
        }
        activateIdentityIfReady(identityId);
        identity = getIdentityById(identityId);
        if (!"ACTIVE".equals(identity.getStatus())) {
            throw new BusinessException("The identity package is not ready. Complete or retry every identity view first.");
        }
        return identity;
    }

    @Override
    public ModelIdentityContext getIdentityContext(Long identityId) {
        ModelIdentity identity = getIdentityById(identityId);
        if (!"ACTIVE".equals(identity.getStatus()) || isBlank(identity.getReferenceImageUrl())) {
            throw new BusinessException("Select an active identity package with a permanent reference image");
        }
        return new ModelIdentityContext(identity.getId(), identity.getIdentityName(), identity.getModelType(),
                identity.getIdentityPrompt(), identity.getNegativePrompt(), identity.getReferenceImageUrl(),
                identity.getImageModel(), identity.getModelVersion(), identity.getSeed());
    }

    @Override
    @Transactional
    public ModelLibrary retryTask(Long id) {
        ModelLibrary model = getModelById(id);
        if (!"FAILED".equals(model.getTaskStatus()) && !"CANCELED".equals(model.getTaskStatus())) {
            throw new BusinessException("Only failed or canceled tasks can be retried");
        }
        ensureTaskCapacity(1);
        submitModelTask(model);
        model.setResultUrl(null);
        model.setCoverImageUrl(null);
        model.setStorageStatus("NOT_REQUESTED");
        model.setStorageError(null);
        return modelLibraryRepository.save(model);
    }

    @Override
    @Transactional
    public ModelLibrary cancelTask(Long id) {
        ModelLibrary model = getModelById(id);
        if ("SUCCESS".equals(model.getTaskStatus()) || "FAILED".equals(model.getTaskStatus())) {
            throw new BusinessException("Completed tasks cannot be canceled");
        }
        model.setTaskStatus("CANCELED");
        model.setStatus("DRAFT");
        return modelLibraryRepository.save(model);
    }

    @Override
    public String getTaskCompletionMode() {
        return isCallbackCompletionEnabled() ? "CALLBACK" : "POLLING";
    }

    private ModelLibrary createModelTaskWithoutCapacityCheck(ModelCreateTaskRequest request) {
        requirePrompt(request.getPrompt());
        ModelLibrary model = request.getModelId() == null ? new ModelLibrary() : getModelById(request.getModelId());
        if (request.getIdentityId() != null) {
            getIdentityById(request.getIdentityId());
        }
        populateModelRecord(model, request, request.getPrompt(), request.getIdentityId(),
                defaultIfBlank(request.getIdentityView(), "REFERENCE"), 1);
        submitModelTask(model);
        return modelLibraryRepository.save(model);
    }

    private ModelLibrary createIdentityAsset(ModelGenerateRequest request, ModelIdentity identity, String view,
                                             int variantIndex, String prompt, Long seed) {
        ModelLibrary model = new ModelLibrary();
        model.setModelName(identity.getIdentityName() + "_" + view + (variantIndex > 1 ? "_" + variantIndex : ""));
        model.setModelType(identity.getModelType());
        model.setEthnicity(identity.getEthnicity());
        model.setAgeRange(identity.getAgeRange());
        model.setStyleTags(identity.getStyleTags());
        model.setIdentityId(identity.getId());
        model.setIdentityView(view);
        model.setVariantIndex(variantIndex);
        model.setGeneratedPrompt(prompt);
        model.setNegativePrompt(identity.getNegativePrompt());
        model.setHairstyle(request.getHairstyle());
        model.setSkinTone(request.getSkinTone());
        model.setCameraAngle(view);
        model.setBackground(request.getBackground());
        model.setClothingDescription(request.getClothingDescription());
        model.setClothingImageUrl(request.getClothingImageUrl());
        model.setImageModel(identity.getImageModel());
        model.setModelVersion(identity.getModelVersion());
        model.setResolution(defaultIfBlank(request.getResolution(), "2K"));
        model.setAspectRatio(defaultIfBlank(request.getAspectRatio(), "3:4"));
        model.setSeed(seed);
        model.setGenerationParamsJson(writeGenerationParams(request, view, variantIndex, seed));
        model.setCreatedBy("system");
        submitModelTask(model);
        return modelLibraryRepository.save(model);
    }

    private ModelLibrary saveFailedIdentityAsset(ModelGenerateRequest request, ModelIdentity identity, String view,
                                                 int variantIndex, String prompt, Long seed, Exception error) {
        ModelLibrary model = new ModelLibrary();
        model.setModelName(identity.getIdentityName() + "_" + view + (variantIndex > 1 ? "_" + variantIndex : ""));
        model.setModelType(identity.getModelType());
        model.setEthnicity(identity.getEthnicity());
        model.setAgeRange(identity.getAgeRange());
        model.setStyleTags(identity.getStyleTags());
        model.setIdentityId(identity.getId());
        model.setIdentityView(view);
        model.setVariantIndex(variantIndex);
        model.setGeneratedPrompt(prompt);
        model.setNegativePrompt(identity.getNegativePrompt());
        model.setHairstyle(request.getHairstyle());
        model.setSkinTone(request.getSkinTone());
        model.setCameraAngle(view);
        model.setBackground(request.getBackground());
        model.setClothingDescription(request.getClothingDescription());
        model.setClothingImageUrl(request.getClothingImageUrl());
        model.setImageModel(identity.getImageModel());
        model.setModelVersion(identity.getModelVersion());
        model.setResolution(defaultIfBlank(request.getResolution(), "2K"));
        model.setAspectRatio(defaultIfBlank(request.getAspectRatio(), "3:4"));
        model.setSeed(seed);
        model.setGenerationParamsJson(writeGenerationParams(request, view, variantIndex, seed));
        model.setTaskStatus("FAILED");
        model.setStatus("DRAFT");
        model.setStorageStatus("NOT_REQUESTED");
        model.setStorageError(error.getMessage());
        model.setCreatedBy("system");
        return modelLibraryRepository.save(model);
    }

    private ModelLibrary saveFailedRecord(ModelCreateTaskRequest request, String prompt, Long identityId,
                                          String view, int variantIndex, Exception error) {
        ModelLibrary model = new ModelLibrary();
        populateModelRecord(model, request, prompt, identityId, defaultIfBlank(view, "REFERENCE"), variantIndex);
        model.setTaskStatus("FAILED");
        model.setStorageError(error.getMessage());
        return modelLibraryRepository.save(model);
    }

    private void populateModelRecord(ModelLibrary model, ModelCreateTaskRequest request, String prompt,
                                     Long identityId, String view, int variantIndex) {
        model.setModelName(defaultIfBlank(request.getModelName(), randomModelName()));
        model.setModelType(defaultIfBlank(request.getModelType(), "Commercial"));
        model.setEthnicity(request.getEthnicity());
        model.setAgeRange(request.getAgeRange());
        model.setBodyType(request.getBodyType());
        model.setStyleTags(request.getStyleTags());
        model.setIdentityId(identityId);
        model.setIdentityView(view);
        model.setVariantIndex(variantIndex);
        model.setGeneratedPrompt(prompt);
        model.setNegativePrompt(defaultIfBlank(request.getNegativePrompt(), DEFAULT_NEGATIVE_PROMPT));
        model.setHairstyle(request.getHairstyle());
        model.setSkinTone(request.getSkinTone());
        model.setCameraAngle(request.getCameraAngle());
        model.setBackground(request.getBackground());
        model.setClothingDescription(request.getClothingDescription());
        model.setClothingImageUrl(request.getClothingImageUrl());
        model.setImageModel(defaultIfBlank(request.getModel(), "nano-banana-pro"));
        model.setModelVersion(defaultIfBlank(request.getModel(), "nano-banana-pro"));
        model.setResolution(defaultIfBlank(request.getResolution(), "2K"));
        model.setAspectRatio(defaultIfBlank(request.getAspectRatio(), "3:4"));
        model.setSeed(request.getSeed() == null ? ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE) : request.getSeed());
        model.setGenerationParamsJson(writeJson(Map.of("source", "manual-create", "identityView", view)));
        model.setTaskStatus("CREATED");
        model.setStatus("DRAFT");
        model.setStorageStatus("NOT_REQUESTED");
        model.setStorageError(null);
        model.setCreatedBy("system");
    }

    private void submitModelTask(ModelLibrary model) {
        String taskId = kieClientService.createTask(
                null,
                model.getGeneratedPrompt(),
                model.getResolution(),
                model.getAspectRatio(),
                model.getImageModel(),
                model.getClothingImageUrl(),
                null,
                appProperties.getKie().getCallbackUrl()
        );
        model.setTaskId(taskId);
        model.setTaskStatus("CREATED");
        model.setStatus("DRAFT");
    }

    private void refreshProcessingTasksInternal(String source) {
        List<ModelLibrary> processingTasks = modelLibraryRepository.findProcessingTasks();
        if (processingTasks.isEmpty()) {
            return;
        }
        log.info("[Model] refresh {} processing tasks via {}", processingTasks.size(), source);
        for (ModelLibrary model : processingTasks) {
            try {
                applyTaskResult(model, kieClientService.getFullResult(model.getTaskId()));
            } catch (Exception e) {
                log.warn("[Model] status refresh failed: id={}, error={}", model.getId(), e.getMessage());
            }
        }
    }

    private void applyTaskResult(ModelLibrary model, KieTaskResult result) {
        if (result == null || !result.isFinished() || "CANCELED".equals(model.getTaskStatus())) {
            return;
        }
        if (result.isSuccess() && !isBlank(result.getResultUrl())) {
            model.setTaskStatus("SUCCESS");
            model.setResultUrl(result.getResultUrl());
            model.setCoverImageUrl(result.getResultUrl());
            model.setStorageStatus("TEMPORARY");
            model.setStorageError(null);
        } else {
            model.setTaskStatus("FAILED");
            model.setStorageStatus("NOT_REQUESTED");
            model.setStorageError(defaultIfBlank(result.getErrorMessage(), "Task completed without a usable result URL"));
        }
        modelLibraryRepository.save(model);
    }

    private ModelLibrary persistResult(ModelLibrary model) {
        if (isBlank(model.getResultUrl())) {
            return markStoragePending(model, "No temporary result URL is available for permanent storage");
        }
        try {
            // 🔴 本地轮询结果落本地 D:/AiResult，不再上 OSS
            String localPath = ossService.downloadResultToLocal("models", String.valueOf(model.getId()), model.getResultUrl());
            if (isBlank(localPath)) {
                return markStoragePending(model, "Local result download returned an empty path");
            }
            String localUrl = ossService.localServingUrl(localPath);
            if (isBlank(localUrl)) {
                return markStoragePending(model, "Local serving URL resolution failed");
            }
            model.setLocalPath(localPath);
            model.setResultUrl(localUrl);
            model.setCoverImageUrl(localUrl);
            model.setStatus("ACTIVE");
            model.setStorageStatus("PERSISTED");
            model.setStorageError(null);
            model.setNextStorageRetryAt(null);
            model.setStorageRetryCount(0);
            ModelLibrary saved = modelLibraryRepository.save(model);
            updateIdentityReference(saved);
            return saved;
        } catch (Exception e) {
            return markStoragePending(model, e.getMessage());
        }
    }

    private ModelLibrary markStoragePending(ModelLibrary model, String error) {
        int retryCount = safeInt(model.getStorageRetryCount()) + 1;
        model.setStatus("STORAGE_PENDING");
        model.setStorageStatus("RETRY_PENDING");
        model.setStorageError(defaultIfBlank(error, "Permanent OSS transfer failed"));
        model.setStorageRetryCount(retryCount);
        model.setNextStorageRetryAt(LocalDateTime.now().plusMinutes(Math.min(30, Math.max(1, retryCount) * 5L)));
        return modelLibraryRepository.save(model);
    }

    private void updateIdentityReference(ModelLibrary asset) {
        if (asset.getIdentityId() == null || isBlank(asset.getCoverImageUrl())) {
            return;
        }
        ModelIdentity identity = getIdentityById(asset.getIdentityId());
        boolean preferredView = "FRONT".equals(asset.getIdentityView()) || "COMPOSITE".equals(asset.getIdentityView());
        if (isBlank(identity.getReferenceImageUrl()) || preferredView) {
            identity.setReferenceImageUrl(asset.getCoverImageUrl());
            identity.setReferenceView(asset.getIdentityView());
            identity.setStorageStatus("PARTIAL");
            modelIdentityRepository.save(identity);
        }
    }

    private void activateIdentityIfReady(Long identityId) {
        if (identityId == null) {
            return;
        }
        ModelIdentity identity = getIdentityById(identityId);
        List<ModelLibrary> assets = modelLibraryRepository.findByIdentityIdOrderByCreatedAtAsc(identityId);
        if (assets.isEmpty()) {
            return;
        }
        boolean complete = assets.stream().allMatch(asset ->
                "SUCCESS".equals(asset.getTaskStatus()) && "PERSISTED".equals(asset.getStorageStatus()));
        if (complete && !isBlank(identity.getReferenceImageUrl())) {
            identity.setStatus("ACTIVE");
            identity.setStorageStatus("PERSISTED");
            identity.setStorageError(null);
        } else if (!"DISABLED".equals(identity.getStatus())) {
            identity.setStatus("DRAFT");
            if (assets.stream().anyMatch(asset -> "RETRY_PENDING".equals(asset.getStorageStatus()))) {
                identity.setStorageStatus("RETRY_PENDING");
            }
        }
        modelIdentityRepository.save(identity);
    }

    private void ensureTaskCapacity(int incomingCount) {
        long activeCount = modelLibraryRepository.countByTaskStatusIn(PROCESSING_STATUSES);
        if (activeCount + incomingCount > MAX_PROCESSING_TASKS) {
            throw new BusinessException("The model task queue is full. A maximum of 10 tasks can run at once.");
        }
    }

    private List<String> normalizeViews(Collection<String> requestedViews, String fallbackAngle) {
        List<String> source = requestedViews == null || requestedViews.isEmpty()
                ? (isBlank(fallbackAngle) || "composite_panel".equalsIgnoreCase(fallbackAngle)
                    ? DEFAULT_IDENTITY_VIEWS
                    : List.of(fallbackAngle))
                : new ArrayList<>(requestedViews);
        List<String> normalized = new ArrayList<>();
        for (String value : source) {
            if (isBlank(value)) {
                continue;
            }
            String view = value.trim().toUpperCase(Locale.ROOT);
            if (List.of("FRONT", "SIDE", "BACK", "EXPRESSION", "THREE_QUARTER", "COMPOSITE").contains(view)
                    && !normalized.contains(view)) {
                normalized.add(view);
            }
        }
        return normalized.isEmpty() ? new ArrayList<>(DEFAULT_IDENTITY_VIEWS) : normalized;
    }

    private String buildIdentityViewPrompt(String identityPrompt, String view, int variantIndex, String negativePrompt) {
        String viewInstruction = switch (view) {
            case "FRONT" -> "Full-body front view. Keep the face, hairstyle, skin tone, proportions, and identity consistent.";
            case "SIDE" -> "Full-body true side profile, 90 degree view. Keep the exact same person and proportions.";
            case "BACK" -> "Full-body back view with a slight head turn only when the face remains plausibly the same person.";
            case "EXPRESSION" -> "Waist-up portrait with a natural expression variation. Keep facial structure, hairstyle, skin tone, and identity identical.";
            case "THREE_QUARTER" -> "Full-body three-quarter view. Keep the exact same person and proportions.";
            case "COMPOSITE" -> "A clear identity reference composite containing a portrait, front, side, and back view of the exact same person.";
            default -> "Keep the exact same person and identity.";
        };
        String variation = variantIndex > 1 ? " Use a different natural pose only; do not change the identity." : "";
        return identityPrompt + "\n\nIDENTITY PACKAGE VIEW: " + viewInstruction + variation +
                "\nNegative constraints: " + negativePrompt;
    }

    private String writeGenerationParams(ModelGenerateRequest request, String view, int variantIndex, Long seed) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("identityView", view);
        params.put("variantIndex", variantIndex);
        params.put("hairstyle", request.getHairstyle());
        params.put("skinTone", request.getSkinTone());
        params.put("background", request.getBackground());
        params.put("clothingDescription", request.getClothingDescription());
        params.put("clothingImageUrl", request.getClothingImageUrl());
        params.put("imageModel", request.getImageModel());
        params.put("resolution", request.getResolution());
        params.put("aspectRatio", request.getAspectRatio());
        params.put("seed", seed);
        return writeJson(params);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String extractOssUrl(String uploadResult) {
        if (isBlank(uploadResult)) {
            return null;
        }
        return Arrays.stream(uploadResult.split("\\|", 2)).findFirst().map(String::trim).orElse(null);
    }

    private boolean isCallbackCompletionEnabled() {
        String callbackUrl = appProperties.getKie().getCallbackUrl();
        if (isBlank(callbackUrl)) {
            return false;
        }
        String lower = callbackUrl.toLowerCase(Locale.ROOT);
        return !lower.contains("localhost") && !lower.contains("127.0.0.1");
    }

    private void requirePrompt(String prompt) {
        if (isBlank(prompt)) {
            throw new BusinessException("Prompt cannot be empty");
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String randomModelName() {
        String[] prefixes = {"Aria", "Bella", "Chloe", "Diana", "Elena", "Fiona", "Grace", "Hannah", "Ivy", "Jade", "Kira", "Luna", "Mia", "Nora", "Olive", "Piper", "Quinn", "Ruby", "Stella", "Tessa", "Uma", "Vera", "Wren", "Xena", "Yara", "Zara"};
        String[] suffixes = {"Studio", "Look", "Style", "Vogue", "Muse", "Aura", "Bloom", "Charm", "Dawn", "Eve", "Flair", "Glow", "Haze", "Iris", "Jewel", "Kiss", "Lush", "Mist", "Nyx", "Opal"};
        return prefixes[ThreadLocalRandom.current().nextInt(prefixes.length)] + "_" +
                suffixes[ThreadLocalRandom.current().nextInt(suffixes.length)];
    }
}
