package com.ai.service.impl;

import com.ai.dto.AplusCopyUpdateRequest;
import com.ai.dto.AplusModuleDefinition;
import com.ai.dto.AplusModuleExtra;
import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusProjectResponse;
import com.ai.entity.AplusTemplate;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusProjectStatus;
import com.ai.enums.AplusTaskStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.repository.AplusTemplateRepository;
import com.ai.service.AplusCopyService;
import com.ai.service.AplusProjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    private final AplusTemplateRepository templateRepository;
    private final AplusCopyService copyService;
    private final ObjectMapper objectMapper;

    @Resource(name = "aplusAsyncExecutor")
    private Executor aplusAsyncExecutor;

    @Override
    @Transactional
    public AplusProjectResponse createProject(AplusProjectCreateRequest request, String operator, String shopName) {
        validateCreateRequest(request);
        List<String> selectedModules = normalizeSelectedModules(request.getSelectedModules());
        String textModel = KieGptModels.normalizeTextModel(request.getTextModel());
        String imageModel = normalizeImageModel(request.getImageModel());
        String resolution = normalizeResolution(request.getResolution());
        LayoutTemplateData layoutTemplate = resolveLayoutTemplate(request);

        AplusProject project = new AplusProject();
        project.setProjectName(request.getProjectName() != null && !request.getProjectName().isBlank()
                ? request.getProjectName().trim()
                : request.getSpu().trim() + "-A+-" + System.currentTimeMillis());
        project.setSpu(request.getSpu().trim());
        project.setReferenceImageUrl(blankToEmpty(request.getReferenceImageUrl()));
        project.setSellingPoints(request.getSellingPoints());
        project.setLayoutTemplateId(layoutTemplate.id());
        project.setLayoutTemplateName(layoutTemplate.name());
        project.setLayoutReferenceImageUrl(layoutTemplate.referenceImageUrl());
        project.setLayoutBlueprintJson(layoutTemplate.blueprintJson());
        project.setSelectedModules(toJson(selectedModules, "[]"));
        project.setStatus(AplusProjectStatus.CREATED.name());
        project.setOperator(operator);
        project.setShopName(shopName);
        project = projectRepository.save(project);

        Map<String, AplusModuleExtra> extras = mergeLayoutTemplateExtras(
                request.getModuleExtras(), layoutTemplate, selectedModules);
        List<AplusImageTask> tasks = new ArrayList<>();
        for (String moduleCode : selectedModules) {
            AplusImageTask task = new AplusImageTask();
            task.setProject(project);
            task.setModuleCode(moduleCode);
            task.setModuleName(AplusModuleDefinition.MODULES.getOrDefault(moduleCode, moduleCode));
            task.setStatus(AplusTaskStatus.PENDING.name());
            task.setAspectRatio("16:9");
            task.setModel(imageModel);
            task.setResolution(resolution);

            AplusModuleExtra extra = extras.get(moduleCode);
            if (extra != null) {
                task.setSupplementaryImageUrl(extra.getMergedSupplementaryImageUrl());
                task.setSupplementaryText(extra.getSupplementaryText());
            }
            tasks.add(task);
        }
        imageTaskRepository.saveAll(tasks);

        Long projectId = project.getId();
        CompletableFuture.runAsync(() -> copyService.generateCopy(projectId, textModel), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] copy generation failed: projectId={}, error={}", projectId, ex.getMessage(), ex);
                    return null;
                });

        log.info("[A+] project created: id={}, spu={}, modules={}, textModel={}, imageModel={}, resolution={}",
                project.getId(), project.getSpu(), tasks.size(), textModel, imageModel, resolution);
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

    private LayoutTemplateData resolveLayoutTemplate(AplusProjectCreateRequest request) {
        Long templateId = request.getLayoutTemplateId();
        String templateName = blankToNull(request.getLayoutTemplateName());
        String referenceImageUrl = blankToNull(request.getLayoutReferenceImageUrl());
        String blueprintJson = blankToNull(request.getLayoutBlueprintJson());

        if (templateId != null) {
            AplusTemplate template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("A+ layout template not found: " + templateId));
            templateName = firstNonBlank(templateName, template.getTemplateName());
            referenceImageUrl = firstNonBlank(referenceImageUrl, template.getLayoutReferenceImageUrl());
            blueprintJson = firstNonBlank(blueprintJson, template.getLayoutBlueprintJson());
        }

        return new LayoutTemplateData(templateId, templateName, referenceImageUrl, blueprintJson);
    }

    private Map<String, AplusModuleExtra> mergeLayoutTemplateExtras(Map<String, AplusModuleExtra> source,
                                                                    LayoutTemplateData layoutTemplate,
                                                                    List<String> selectedModules) {
        Map<String, AplusModuleExtra> result = new HashMap<>();
        if (source != null) {
            result.putAll(source);
        }
        if (!layoutTemplate.hasLayout()) {
            return result;
        }

        for (String moduleCode : selectedModules) {
            AplusModuleExtra extra = result.getOrDefault(moduleCode, new AplusModuleExtra());
            List<String> urls = new ArrayList<>();
            if (layoutTemplate.referenceImageUrl() != null && !layoutTemplate.referenceImageUrl().isBlank()) {
                urls.add(layoutTemplate.referenceImageUrl().trim());
            }
            if (extra.getSupplementaryImageUrls() != null) {
                urls.addAll(extra.getSupplementaryImageUrls());
            }
            extra.setSupplementaryImageUrls(dedupeUrls(urls));
            extra.setSupplementaryText(mergeText(
                    buildLayoutTemplateInstruction(moduleCode, layoutTemplate),
                    extra.getSupplementaryText()));
            result.put(moduleCode, extra);
        }
        return result;
    }

    private String buildLayoutTemplateInstruction(String moduleCode, LayoutTemplateData layoutTemplate) {
        JsonNode root = readJsonOrNull(layoutTemplate.blueprintJson());
        JsonNode global = root != null ? root.path("globalStyle") : null;
        JsonNode modules = root != null ? root.path("modules") : null;
        JsonNode module = modules != null ? modules.path(moduleCode) : null;
        return """
                Structure template mode:
                - Use the selected A+ layout template for layout structure only.
                - Generate only this AD module as one standalone 16:9 image; do not generate a full long infographic page.
                - If an A+ reference image is provided, use it only for A+ layout structure, visual hierarchy, panel rhythm, typography placement, image crop rhythm, and information density.
                - Do not use the A+ reference image as the garment or product source of truth. Garment identity comes from product information, module-specific instructions, and supplementary product images when provided.
                - The layout reference image is only for hierarchy, panel rhythm, rounded card style, image crop rhythm, text placement, and information density.
                - Do not copy the reference product, model gender, faces, poses, product colors, brand logo, exact scene, accessories, or exact photos.
                - All visible text inside the final image must be English only; no Chinese characters or bilingual captions.
                Layout template: %s
                Layout reference image URL: %s
                Global layout style JSON: %s
                Module %s blueprint JSON: %s
                """.formatted(
                layoutTemplate.name() == null ? "" : layoutTemplate.name(),
                layoutTemplate.referenceImageUrl() == null ? "" : layoutTemplate.referenceImageUrl(),
                global != null && !global.isMissingNode() ? global.toString() : "{}",
                moduleCode,
                module != null && !module.isMissingNode() ? module.toString() : "{}");
    }

    private JsonNode readJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> dedupeUrls(List<String> urls) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                normalized.add(url.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private String mergeText(String first, String second) {
        if (first == null || first.isBlank()) {
            return blankToNull(second);
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "\n\n" + second.trim();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : blankToNull(second);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private record LayoutTemplateData(Long id, String name, String referenceImageUrl, String blueprintJson) {
        boolean hasLayout() {
            return (referenceImageUrl != null && !referenceImageUrl.isBlank())
                    || (blueprintJson != null && !blueprintJson.isBlank());
        }
    }

    private void validateCreateRequest(AplusProjectCreateRequest request) {
        if (request.getSpu() == null || request.getSpu().isBlank()) {
            throw new RuntimeException("SPU 不能为空");
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

    private String normalizeImageModel(String imageModel) {
        if (imageModel == null || imageModel.isBlank()) {
            return "nano-banana-pro";
        }
        String normalized = imageModel.trim();
        if ("nano-banana-pro".equals(normalized) || "gpt-image-2-image-to-image".equals(normalized)) {
            return normalized;
        }
        throw new RuntimeException("不支持的图片模型: " + imageModel);
    }

    private String normalizeResolution(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            return "2K";
        }
        String normalized = resolution.trim().toUpperCase();
        if ("1K".equals(normalized) || "2K".equals(normalized) || "4K".equals(normalized)) {
            return normalized;
        }
        throw new RuntimeException("不支持的分辨率: " + resolution);
    }

    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }
}
