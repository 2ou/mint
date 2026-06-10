package com.ai.service.impl;

import com.ai.dto.AplusCopyUpdateRequest;
import com.ai.dto.AplusModuleDefinition;
import com.ai.dto.AplusModuleExtra;
import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusProjectResponse;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusProjectStatus;
import com.ai.enums.AplusTaskStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.service.AplusCopyService;
import com.ai.service.AplusProjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AplusProjectServiceImpl implements AplusProjectService {

    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    private final AplusCopyService copyService;
    private final ObjectMapper objectMapper;

    @Resource(name = "aplusAsyncExecutor")
    private Executor aplusAsyncExecutor;

    @Override
    @Transactional
    public AplusProjectResponse createProject(AplusProjectCreateRequest request, String operator, String shopName) {
        validateCreateRequest(request);
        List<String> selectedModules = normalizeSelectedModules(request.getSelectedModules());

        AplusProject project = new AplusProject();
        project.setProjectName(request.getProjectName() != null && !request.getProjectName().isBlank()
                ? request.getProjectName().trim()
                : request.getSpu().trim() + "-A+-" + System.currentTimeMillis());
        project.setSpu(request.getSpu().trim());
        project.setReferenceImageUrl(request.getReferenceImageUrl().trim());
        project.setSellingPoints(request.getSellingPoints());
        project.setSelectedModules(toJson(selectedModules, "[]"));
        project.setStatus(AplusProjectStatus.CREATED.name());
        project.setOperator(operator);
        project.setShopName(shopName);
        project = projectRepository.save(project);

        Map<String, AplusModuleExtra> extras = request.getModuleExtras() != null ? request.getModuleExtras() : Map.of();
        List<AplusImageTask> tasks = new ArrayList<>();
        for (String moduleCode : selectedModules) {
            AplusImageTask task = new AplusImageTask();
            task.setProject(project);
            task.setModuleCode(moduleCode);
            task.setModuleName(AplusModuleDefinition.MODULES.getOrDefault(moduleCode, moduleCode));
            task.setStatus(AplusTaskStatus.PENDING.name());
            task.setAspectRatio("16:9");

            AplusModuleExtra extra = extras.get(moduleCode);
            if (extra != null) {
                task.setSupplementaryImageUrl(extra.getMergedSupplementaryImageUrl());
                task.setSupplementaryText(extra.getSupplementaryText());
            }
            tasks.add(task);
        }
        imageTaskRepository.saveAll(tasks);

        Long projectId = project.getId();
        CompletableFuture.runAsync(() -> copyService.generateCopy(projectId), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] copy generation failed: projectId={}, error={}", projectId, ex.getMessage(), ex);
                    return null;
                });

        log.info("[A+] project created: id={}, spu={}, modules={}", project.getId(), project.getSpu(), tasks.size());
        return AplusProjectResponse.from(project);
    }

    @Override
    @Transactional(readOnly = true)
    public AplusProjectResponse getProjectById(Long id) {
        AplusProject project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + id));
        return AplusProjectResponse.from(project);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AplusProjectResponse> getProjectPage(int page, int size, String spu, String status) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AplusProject> result;
        if (spu != null && !spu.isBlank() && status != null && !status.isBlank()) {
            result = projectRepository.findBySpuContainingAndStatusIn(spu, List.of(status), pageable);
        } else if (spu != null && !spu.isBlank()) {
            result = projectRepository.findBySpuContaining(spu, pageable);
        } else if (status != null && !status.isBlank()) {
            result = projectRepository.findByStatusIn(List.of(status), pageable);
        } else {
            result = projectRepository.findAll(pageable);
        }
        return result.map(AplusProjectResponse::from);
    }

    @Override
    @Transactional
    public AplusProjectResponse updateCopy(Long id, AplusCopyUpdateRequest request) {
        AplusProject project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + id));

        if (request.getAplusMarkdown() != null) {
            project.setAplusMarkdown(request.getAplusMarkdown());
        }

        Map<String, String> moduleCopies = request.getModuleCopies();
        if (moduleCopies != null && !moduleCopies.isEmpty()) {
            List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(id);
            for (AplusImageTask task : tasks) {
                if (moduleCopies.containsKey(task.getModuleCode())) {
                    task.setModuleCopy(moduleCopies.get(task.getModuleCode()));
                    if (AplusTaskStatus.PENDING.name().equals(task.getStatus())) {
                        task.setPrompt(null);
                    }
                }
            }
            imageTaskRepository.saveAll(tasks);
        }

        project.setStatus(AplusProjectStatus.COPY_DONE.name());
        project.setErrorMessage(null);
        projectRepository.save(project);
        return getProjectById(id);
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        AplusProject project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + id));
        projectRepository.delete(project);
        log.info("[A+] project deleted: id={}", id);
    }

    @Override
    @Transactional
    public void updateProjectStatus(Long id, String status, String errorMessage) {
        AplusProject project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + id));
        project.setStatus(status);
        if (errorMessage != null) {
            project.setErrorMessage(errorMessage);
        }
        projectRepository.save(project);
    }

    private void validateCreateRequest(AplusProjectCreateRequest request) {
        if (request.getSpu() == null || request.getSpu().isBlank()) {
            throw new RuntimeException("SPU 不能为空");
        }
        if (request.getReferenceImageUrl() == null || request.getReferenceImageUrl().isBlank()) {
            throw new RuntimeException("参考图不能为空");
        }
        if (request.getSelectedModules() == null || request.getSelectedModules().isEmpty()) {
            throw new RuntimeException("请至少选择一个 A+ 模块");
        }
    }

    private List<String> normalizeSelectedModules(List<String> selectedModules) {
        List<String> normalized = selectedModules.stream()
                .filter(code -> code != null && AplusModuleDefinition.MODULES.containsKey(code))
                .distinct()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            throw new RuntimeException("没有有效的 A+ 模块");
        }
        return normalized;
    }

    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }
}
