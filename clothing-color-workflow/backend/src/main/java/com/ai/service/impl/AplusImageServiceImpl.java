package com.ai.service.impl;

import com.ai.dto.AplusModuleDefinition;
import com.ai.dto.KieTaskResult;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusProjectStatus;
import com.ai.enums.AplusTaskStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.service.AplusImageService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AplusImageServiceImpl implements AplusImageService {

    private final KieClientService kieClientService;
    private final OssService ossService;
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;

    @Resource(name = "aplusAsyncExecutor")
    private Executor aplusAsyncExecutor;

    @Value("${aplus.image.model:nano-banana-pro}")
    private String defaultModel;

    @Override
    @Transactional
    public void generateImages(Long projectId) {
        AplusProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + projectId));

        if (!AplusProjectStatus.COPY_DONE.name().equals(project.getStatus())
                && !AplusProjectStatus.PARTIAL_FAILED.name().equals(project.getStatus())) {
            throw new RuntimeException("请先完成并确认文案，再生成图片");
        }

        project.setStatus(AplusProjectStatus.GENERATING_IMAGES.name());
        project.setErrorMessage(null);
        projectRepository.save(project);

        List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(projectId);
        int submitted = 0;
        for (AplusImageTask task : tasks) {
            if (!AplusTaskStatus.PENDING.name().equals(task.getStatus())) {
                continue;
            }
            if (submitTask(project, task)) {
                submitted++;
            }
        }

        if (submitted == 0) {
            checkProjectCompletion(projectId);
        }
        log.info("[A+] image generation submitted: projectId={}, submitted={}", projectId, submitted);
    }

    @Override
    @Transactional
    public void regenerateModule(Long projectId, String moduleCode) {
        AplusProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + projectId));
        AplusImageTask task = imageTaskRepository.findByProjectIdAndModuleCode(projectId, moduleCode).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("模块任务不存在: " + moduleCode));

        resetForRegeneration(task);
        project.setStatus(AplusProjectStatus.GENERATING_IMAGES.name());
        project.setErrorMessage(null);
        projectRepository.save(project);
        if (!submitTask(project, task)) {
            checkProjectCompletion(projectId);
        }
    }

    @Override
    @Transactional
    public int retryFailedModules(Long projectId) {
        AplusProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + projectId));
        List<AplusImageTask> failedTasks = imageTaskRepository.findByProjectId(projectId).stream()
                .filter(task -> AplusTaskStatus.FAILED.name().equals(task.getStatus()))
                .collect(Collectors.toList());
        if (failedTasks.isEmpty()) {
            return 0;
        }

        project.setStatus(AplusProjectStatus.GENERATING_IMAGES.name());
        project.setErrorMessage(null);
        projectRepository.save(project);

        for (AplusImageTask task : failedTasks) {
            resetForRegeneration(task);
            submitTask(project, task);
        }
        checkProjectCompletion(projectId);
        return failedTasks.size();
    }

    @Scheduled(fixedRate = 30000)
    public void pollTaskStatus() {
        List<AplusImageTask> processingTasks = imageTaskRepository.findByStatus(AplusTaskStatus.PROCESSING.name());
        if (processingTasks.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = processingTasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> refreshTask(task), aplusAsyncExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private boolean submitTask(AplusProject project, AplusImageTask task) {
        try {
            String prompt = buildModulePrompt(task, project);
            String kieTaskId = kieClientService.createTask(
                    project.getSpu(),
                    prompt,
                    "2K",
                    task.getAspectRatio() != null ? task.getAspectRatio() : "16:9",
                    defaultModel,
                    project.getReferenceImageUrl(),
                    task.getSupplementaryImageUrl()
            );

            task.setPrompt(prompt);
            task.setKieTaskId(kieTaskId);
            task.setStatus(AplusTaskStatus.PROCESSING.name());
            task.setModel(defaultModel);
            task.setErrorMessage(null);
            imageTaskRepository.save(task);
            return true;
        } catch (Exception e) {
            log.error("[A+] submit module failed: projectId={}, module={}, error={}",
                    project.getId(), task.getModuleCode(), e.getMessage(), e);
            task.setStatus(AplusTaskStatus.FAILED.name());
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            imageTaskRepository.save(task);
            return false;
        }
    }

    private void refreshTask(AplusImageTask task) {
        try {
            KieTaskResult result = kieClientService.getFullResult(task.getKieTaskId());
            if (result == null) {
                return;
            }

            Long projectId = task.getProject().getId();
            if (result.isSuccess() || "SUCCESS".equalsIgnoreCase(result.getStatus())) {
                task.setResultTempUrl(result.getResultUrl());
                task.setStatus(AplusTaskStatus.SUCCESS.name());
                task.setCompletedAt(LocalDateTime.now());
                imageTaskRepository.save(task);

                try {
                    AplusProject project = projectRepository.findById(projectId)
                            .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + projectId));
                    String ossResult = ossService.uploadResultToOss(
                            project.getSpu(),
                            result.getResultUrl(),
                            task.getId(),
                            true
                    );
                    task.setResultOssUrl(extractOssUrl(ossResult));
                    imageTaskRepository.save(task);
                } catch (Exception e) {
                    log.error("[A+] OSS transfer failed: module={}, error={}", task.getModuleCode(), e.getMessage());
                }

                checkProjectCompletion(projectId);
            } else if (result.isFinished() && !result.isSuccess()
                    || "FAILED".equalsIgnoreCase(result.getStatus())) {
                task.setStatus(AplusTaskStatus.FAILED.name());
                task.setErrorMessage(result.getErrorMessage());
                task.setCompletedAt(LocalDateTime.now());
                imageTaskRepository.save(task);
                checkProjectCompletion(projectId);
            }
        } catch (Exception e) {
            log.error("[A+] refresh module failed: module={}, kieTaskId={}, error={}",
                    task.getModuleCode(), task.getKieTaskId(), e.getMessage(), e);
        }
    }

    private void checkProjectCompletion(Long projectId) {
        List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(projectId);
        boolean allDone = tasks.stream().allMatch(task ->
                AplusTaskStatus.SUCCESS.name().equals(task.getStatus())
                        || AplusTaskStatus.FAILED.name().equals(task.getStatus()));
        if (!allDone) {
            return;
        }

        AplusProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

        List<String> failedModules = tasks.stream()
                .filter(task -> AplusTaskStatus.FAILED.name().equals(task.getStatus()))
                .map(AplusImageTask::getModuleCode)
                .collect(Collectors.toList());
        if (failedModules.isEmpty()) {
            project.setStatus(AplusProjectStatus.COMPLETED.name());
            project.setErrorMessage(null);
        } else {
            project.setStatus(AplusProjectStatus.PARTIAL_FAILED.name());
            project.setErrorMessage("以下模块生成失败: " + String.join(", ", failedModules));
        }
        project.setCompletedAt(LocalDateTime.now());
        projectRepository.save(project);
    }

    private void resetForRegeneration(AplusImageTask task) {
        task.setStatus(AplusTaskStatus.PENDING.name());
        task.setKieTaskId(null);
        task.setResultTempUrl(null);
        task.setResultOssUrl(null);
        task.setErrorMessage(null);
        task.setCompletedAt(null);
        imageTaskRepository.save(task);
    }

    private String buildModulePrompt(AplusImageTask task, AplusProject project) {
        String moduleCode = task.getModuleCode();
        StringBuilder sb = new StringBuilder();
        sb.append("Create one premium e-commerce product-detail content image for women's fashion.\n\n");
        sb.append("Module: ").append(moduleCode).append(" ").append(task.getModuleName()).append("\n");
        sb.append("Product SPU: ").append(project.getSpu()).append("\n");
        sb.append("Reference product image must be followed exactly: same product, same print, same fabric, same color family.\n\n");

        sb.append("Brand/Style Consistency:\n");
        sb.append(AplusModuleDefinition.STYLE_ANCHOR).append("\n\n");

        sb.append("Module Copy and Visual Brief:\n");
        sb.append(task.getModuleCopy() != null ? task.getModuleCopy() : project.getSellingPoints()).append("\n\n");

        String visualPosition = AplusModuleDefinition.VISUAL_POSITIONS.getOrDefault(moduleCode, "");
        if (!visualPosition.isBlank()) {
            sb.append("Required Layout: ").append(visualPosition).append("\n\n");
        }

        if (task.getSupplementaryText() != null && !task.getSupplementaryText().isBlank()) {
            sb.append("Additional Module Instructions:\n").append(task.getSupplementaryText()).append("\n\n");
        }

        if ("AD-06".equals(moduleCode) || "AD-07".equals(moduleCode)) {
            sb.append("Text Handling Rule: do NOT render readable words, numbers, labels, or size chart text inside the image. ");
            sb.append("Create clean blank content zones, placeholders, panels, or soft label areas where the system can overlay real text later. ");
            sb.append("Keep the layout premium, clear, and editorial.\n\n");
        } else {
            sb.append("No random text overlays, no misspelled text, no fake labels.\n\n");
        }

        sb.append("Image Specification: 16:9 aspect ratio, 2K resolution, premium e-commerce detail-page visual, realistic product material, clean commercial lighting.");
        return sb.toString();
    }

    private String extractOssUrl(String ossResult) {
        if (ossResult == null || ossResult.isBlank()) {
            return null;
        }
        int separator = ossResult.indexOf('|');
        return separator >= 0 ? ossResult.substring(0, separator) : ossResult;
    }
}
