package com.ai.service.impl;

import com.ai.dto.AplusModuleDefinition;
import com.ai.dto.AplusReferenceImage;
import com.ai.dto.KieTaskResult;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusImageTaskVersion;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusProjectStatus;
import com.ai.enums.AplusTaskStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusImageTaskVersionRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.service.AplusImageService;
import com.ai.service.AplusQualityService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import com.ai.config.AppProperties;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final AplusImageTaskVersionRepository versionRepository;
    private final AplusQualityService qualityService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

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
        List<AplusImageTask> pendingTasks = tasks.stream()
                .filter(task -> AplusTaskStatus.PENDING.name().equals(task.getStatus()))
                .toList();

        if (pendingTasks.isEmpty()) {
            checkProjectCompletion(projectId);
            return;
        }

        // 并行提交所有模块任务
        List<CompletableFuture<Boolean>> futures = pendingTasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> submitTask(project, task), aplusAsyncExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long submitted = futures.stream().filter(CompletableFuture::join).count();

        if (submitted == 0) {
            checkProjectCompletion(projectId);
        }
        log.info("[A+] image generation submitted: projectId={}, submitted={}/{}", projectId, submitted, pendingTasks.size());
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

        // 并行重试所有失败任务，不在这里检查完成状态（由轮询统一处理）
        failedTasks.forEach(this::resetForRegeneration);
        List<CompletableFuture<Boolean>> futures = failedTasks.stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> submitTask(project, task), aplusAsyncExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return failedTasks.size();
    }

    @Scheduled(fixedRate = 15000)
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
            String resolution = effectiveResolution(task);
            String imageModel = effectiveModel(task);
            List<AplusReferenceImage> references = referenceImages(task, project);
            String callbackUrl = appProperties.getKie().getCallbackUrl();
            String kieTaskId = kieClientService.createTask(
                    project.getSpu(),
                    prompt,
                    resolution,
                    effectiveAspectRatio(task),
                    imageModel,
                    firstReferenceUrl(references, AplusReferenceImage.PRODUCT_TRUTH),
                    supportingReferenceUrls(references),
                    callbackUrl
            );

            task.setPrompt(prompt);
            task.setKieTaskId(kieTaskId);
            task.setStatus(AplusTaskStatus.PROCESSING.name());
            task.setModel(imageModel);
            task.setResolution(resolution);
            task.setErrorMessage(null);
            task.setQualityStatus("NOT_EVALUATED");
            task.setQualityReportJson(null);
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
                try {
                    // 🔴 本地轮询结果落本地 D:/AiResult，不再上 OSS
                    String localPath = ossService.downloadResultToLocal(
                            task.getProject().getSpu(),
                            task.getKieTaskId() == null || task.getKieTaskId().isBlank()
                                    ? String.valueOf(task.getId()) : task.getKieTaskId(),
                            result.getResultUrl());
                    if (localPath != null) {
                        task.setLocalPath(localPath);
                        String localUrl = ossService.localServingUrl(localPath);
                        if (localUrl != null) task.setResultOssUrl(localUrl);
                    }
                    task.setStatus(AplusTaskStatus.SUCCESS.name());
                    task.setErrorMessage(null);
                    task.setCompletedAt(LocalDateTime.now());
                    imageTaskRepository.save(task);
                } catch (Exception e) {
                    // 本地落盘失败：保留 KIE 临时链接，任务仍标记成功（图已生成，仅未缓存到本地）
                    log.warn("[A+] 本地落盘失败，保留 KIE 临时链接: module={}, error={}", task.getModuleCode(), e.getMessage());
                    task.setStatus(AplusTaskStatus.SUCCESS.name());
                    task.setErrorMessage(null);
                    task.setCompletedAt(LocalDateTime.now());
                    imageTaskRepository.save(task);
                }

                Long completedTaskId = task.getId();
                CompletableFuture.runAsync(() -> qualityService.evaluate(completedTaskId), aplusAsyncExecutor);
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
        archiveCurrentVersion(task);
        task.setStatus(AplusTaskStatus.PENDING.name());
        task.setKieTaskId(null);
        task.setResultTempUrl(null);
        task.setResultOssUrl(null);
        task.setLocalPath(null);
        task.setErrorMessage(null);
        task.setQualityStatus("NOT_EVALUATED");
        task.setQualityReportJson(null);
        task.setCompletedAt(null);
        imageTaskRepository.save(task);
    }

    private void archiveCurrentVersion(AplusImageTask task) {
        if (task.getKieTaskId() == null && task.getResultTempUrl() == null && task.getResultOssUrl() == null
                && task.getPrompt() == null) {
            task.setVersionNumber(task.getVersionNumber() == null ? 1 : task.getVersionNumber());
            return;
        }
        AplusImageTaskVersion version = new AplusImageTaskVersion();
        version.setProjectId(task.getProject().getId());
        version.setTaskId(task.getId());
        version.setVersionNumber(task.getVersionNumber() == null ? 1 : task.getVersionNumber());
        version.setModuleCode(task.getModuleCode());
        version.setKieTaskId(task.getKieTaskId());
        version.setPrompt(task.getPrompt());
        version.setReferenceImagesJson(task.getReferenceImagesJson());
        version.setAspectRatio(task.getAspectRatio());
        version.setResolution(task.getResolution());
        version.setModel(task.getModel());
        version.setStatus(task.getStatus());
        version.setResultTempUrl(task.getResultTempUrl());
        version.setResultOssUrl(task.getResultOssUrl());
        version.setQualityReportJson(task.getQualityReportJson());
        versionRepository.save(version);
        task.setVersionNumber(version.getVersionNumber() + 1);
    }

    // ── 模块 Prompt 数据：布局规范 + 文案指导 + 生成模式，一处定义，不再散落 ──
    private static final Map<String, String> MODULE_LAYOUTS = new LinkedHashMap<>();
    private static final Map<String, String> MODULE_TEXT_GUIDES = new LinkedHashMap<>();
    private static final Set<String> MODEL_SCENE_MODULES = Set.of("AD-01", "AD-04", "AD-05");

    static {
        MODULE_LAYOUTS.put("AD-01",
                "Brand hero banner. Premium first impression with realistic American model wearing the user's product. " +
                "Studio or clean lifestyle setting. Frame garment head to hip/waist, place headline/subline in clean text area.");
        MODULE_TEXT_GUIDES.put("AD-01",
                "Hero headline + subline, e.g. Effortless Everyday Comfort; Flowy fit with polished Henley neckline.");

        MODULE_LAYOUTS.put("AD-02",
                "Split layout: product flat-lay/cutout/folded on one side, macro fabric/print texture on the other. " +
                "Soft divider, tactile realism, readable fabric/feel labels, no model unless provided.");
        MODULE_TEXT_GUIDES.put("AD-02",
                "2-4 fabric/feel labels, e.g. Soft Draping Fabric; Smooth Touch; Lightweight Feel.");

        MODULE_LAYOUTS.put("AD-03",
                "Center product flat-lay/cutout, surround with 3-4 close-up inset panels for real details (neckline, sleeve, hem, stitching). " +
                "Thin connector lines, readable detail labels.");
        MODULE_TEXT_GUIDES.put("AD-03",
                "3-4 detail labels tied to visible features, e.g. V-Neck Henley; Three-Button Detail; Curved Hem.");

        MODULE_LAYOUTS.put("AD-04",
                "Three American lifestyle panels, consistent model wearing same product in different scenarios (coffee shop, office, street). " +
                "Short caption per panel, identical garment identity across all.");
        MODULE_TEXT_GUIDES.put("AD-04",
                "One caption per panel, e.g. Coffee Run; Workday Casual; Weekend Ready.");

        MODULE_LAYOUTS.put("AD-05",
                "Realistic American model in relaxed pose emphasizing drape, coverage, movement, flattering fit. " +
                "One fabric/fit detail inset, readable comfort/fit labels.");
        MODULE_TEXT_GUIDES.put("AD-05",
                "2-4 comfort/fit labels, e.g. Relaxed Fit; Soft Drape; Comfortable Coverage; Moves With You.");

        MODULE_LAYOUTS.put("AD-06",
                "Technical layout: front product view, measurement arrows, and a clean blank chart panel reserved for deterministic canvas text. " +
                "Use fit labels only inside the generated image; never rasterize numeric measurements.");
        MODULE_TEXT_GUIDES.put("AD-06",
                "Fit labels plus an empty chart panel; exact supplied values are rendered as canvas overlay text.");

        MODULE_LAYOUTS.put("AD-07",
                "Still-life with folded garment or neat arrangement, care/quality visual cues, readable care explanation panel.");
        MODULE_TEXT_GUIDES.put("AD-07",
                "Care/quality text, e.g. Gentle Machine Wash; Easy Care; Soft Drape; Made for Everyday Wear.");
    }

    private static final String SHARED_NEGATIVE =
            "No unsupported garment design, wrong pattern, arbitrary color changes, incorrect neckline, missing/extra buttons, " +
            "duplicated sleeves, extra collars, melted/distorted fabric, plastic/CGI texture, Chinese text, bilingual text, " +
            "unreadable/fake/misspelled text, random typography, watermark, logo, barcode, messy collage, low-res artifacts.";

    private static final String MODEL_SCENE_NEGATIVE =
            "No full-body invention from cropped reference, influencer face, porcelain skin, ultra-thin body for plus-size, " +
            "stiff pose, Chinese-style scene/architecture/furniture, over-saturated filters, warped body/hands, random props hiding garment, " + SHARED_NEGATIVE;

    private static final String PRODUCT_NEGATIVE =
            "No new model, human body, face, hands, hanger, mannequin, worn view, lifestyle scene " +
            "unless supplemental references explicitly request them, " + SHARED_NEGATIVE;

    private String buildModulePrompt(AplusImageTask task, AplusProject project) {
        String code = task.getModuleCode();
        String aspectRatio = effectiveAspectRatio(task);
        List<AplusReferenceImage> references = referenceImages(task, project);
        boolean hasProductTruth = !firstReferenceUrl(references, AplusReferenceImage.PRODUCT_TRUTH).isBlank();
        boolean hasLayoutReference = !firstReferenceUrl(references, AplusReferenceImage.LAYOUT).isBlank();
        String moduleLayout = MODULE_LAYOUTS.getOrDefault(code, "Premium e-commerce A+ module layout.");
        String moduleTextGuide = MODULE_TEXT_GUIDES.getOrDefault(code, "Short headline + 2-3 product-benefit labels.");
        boolean modelScene = MODEL_SCENE_MODULES.contains(code);

        StringBuilder sb = new StringBuilder(2048);
        // ── 任务概述 ──
        sb.append("Create one production-ready ").append(aspectRatio)
                .append(" A+ module image for women's fashion e-commerce.\n");
        sb.append(hasProductTruth
                ? "Image-to-image: image input [0] is the immutable product truth source.\n\n"
                : "No product truth image is available. Use supplied product information only and do not invent details.\n\n");

        // ── 模块 & 产品 ──
        sb.append("Module: ").append(code).append(" ").append(task.getModuleName()).append("\n");
        sb.append("SPU: ").append(project.getSpu()).append("\n");
        sb.append("Product truth image: ").append(hasProductTruth ? "provided" : "not provided").append("\n");
        sb.append("Layout reference image: ").append(hasLayoutReference ? "provided" : "not provided").append("\n\n");

        // ── Image Input 角色 ──
        appendImageInputRoleMap(sb, references, project);

        // ── 参考图使用规则 ──
        sb.append("Product Fidelity Rules: image input [0] is the highest-priority garment source of truth. " +
                "Preserve its category, silhouette, color family, print, fabric texture, neckline, sleeves, hem, trims, and visible construction. " +
                "Do not redesign the garment or let any layout/supplementary image override it.\n");
        if (hasLayoutReference) {
            sb.append("Layout Reference Rules: the layout reference controls hierarchy, card rhythm, crop style, text-safe zones, " +
                    "and information density only. Never copy its product, model, face, pose, brand, logo, color palette, or exact scene.\n");
        }
        sb.append("\n");

        // ── 产品构建 + 生成模式 ──
        sb.append("Product: match SPU, selling points, module copy. No invented logos/prints/hardware/category changes. " +
                "Product clarity > scene creativity.\n");
        appendJsonContext(sb, "Product analysis", project.getProductAnalysisJson());
        sb.append(modelScene
                ? "Mode: realistic American model + authentic lifestyle scene. Dress in user's product. " +
                  "Model: Curve/Plus-Size or Commercial/Catalog woman, 30-42, natural skin, body-positive. " +
                  "Scene: modern apartment, coffee shop, casual office, NYC/LA street, garden/patio. " +
                  "Minimal accessories.\n\n"
                : "Mode: product-focused (cutout/flat-lay/folded/fabric macro/detail insets/size chart/care still-life). " +
                  "No new model or scene unless user instructions request it.\n\n");

        // ── 风格锚点 + 布局 ──
        sb.append("Style: ").append(AplusModuleDefinition.STYLE_ANCHOR).append("\n\n");
        appendJsonContext(sb, "Project visual system", project.getVisualSystemJson());
        sb.append("Layout: ").append(moduleLayout).append("\n\n");

        // ── 文案 ──
        sb.append("Module Copy:\n").append(task.getModuleCopy() != null ? task.getModuleCopy() : project.getSellingPoints()).append("\n\n");
        sb.append("Required Text: ").append(moduleTextGuide).append("\n\n");

        // ── 补充信息 ──
        if (task.getSupplementaryText() != null && !task.getSupplementaryText().isBlank()) {
            sb.append("Additional Instructions: ").append(task.getSupplementaryText()).append("\n");
        }
        if (task.getSupplementaryImageUrl() != null && !task.getSupplementaryImageUrl().isBlank()) {
            sb.append("Supplementary image(s) provided: use for module-specific detail/scene/fabric/fit only. " +
                    "Do not copy unrelated brands/models/faces/scenes.\n");
        }

        // ── 文字规则（合并英文约束 + 文案处理） ──
        sb.append("\nText Rules: English only — no Chinese characters, bilingual captions, or mixed typography. " +
                "Render concise readable text from module copy + required text guidance. " +
                "For AD-06 with size data, reproduce exact measurements as compact chart. " +
                "Clean typography, high-contrast, premium alignment. " +
                "No random words, fake brand names, fake measurements, or unrelated labels.\n\n");

        // ── 质量标准 ──
        if ("AD-06".equals(code)) {
            sb.append("AD-06 rendering rule: reserve a clean blank chart panel and do not render numeric size data inside the image; exact numbers are overlaid deterministically on the canvas.\n\n");
        }

        sb.append("Quality: sharp edges, realistic drape/folds, clean lighting, balanced spacing, clear hierarchy, " +
                "consistent palette, safe margins, premium catalog finish.\n\n");

        // ── 负面约束 ──
        sb.append("Negative: ").append(modelScene ? MODEL_SCENE_NEGATIVE : PRODUCT_NEGATIVE).append("\n\n");

        // ── 输出规格 ──
        sb.append("Output: ").append(aspectRatio).append(", ")
                .append(effectiveResolution(task)).append(", single finished image.");
        return sb.toString();
    }

    private void appendImageInputRoleMap(StringBuilder sb, List<AplusReferenceImage> references, AplusProject project) {
        sb.append("Image Inputs:\n");
        if (references.isEmpty()) {
            sb.append("- No image input available.\n");
        } else {
            for (int index = 0; index < references.size(); index++) {
                AplusReferenceImage reference = references.get(index);
                String role = reference.getRole() == null ? AplusReferenceImage.SUPPLEMENTARY : reference.getRole();
                sb.append("- [").append(index).append("] ").append(role).append(": ")
                        .append(reference.getNote() == null ? "" : reference.getNote()).append("\n");
            }
        }
        if (project.getLayoutTemplateName() != null && !project.getLayoutTemplateName().isBlank()) {
            sb.append("- Layout template: ").append(project.getLayoutTemplateName()).append(" (structure only).\n");
        }
        sb.append("\n");
    }

    private void appendJsonContext(StringBuilder sb, String label, String json) {
        if (json != null && !json.isBlank()) {
            sb.append(label).append(": ").append(json).append("\n\n");
        }
    }

    private List<AplusReferenceImage> referenceImages(AplusImageTask task, AplusProject project) {
        List<AplusReferenceImage> parsed = new ArrayList<>();
        if (task.getReferenceImagesJson() != null && !task.getReferenceImagesJson().isBlank()) {
            try {
                parsed.addAll(objectMapper.readValue(task.getReferenceImagesJson(),
                        new TypeReference<List<AplusReferenceImage>>() {}));
            } catch (Exception e) {
                log.warn("[A+] typed reference parse failed: taskId={}, error={}", task.getId(), e.getMessage());
            }
        }
        if (parsed.isEmpty()) {
            addReference(parsed, AplusReferenceImage.PRODUCT_TRUTH, project.getReferenceImageUrl(),
                    "Primary product source of truth.");
            addReference(parsed, AplusReferenceImage.LAYOUT, project.getLayoutReferenceImageUrl(),
                    "Layout structure only.");
            if (task.getSupplementaryImageUrl() != null) {
                for (String url : task.getSupplementaryImageUrl().split(",")) {
                    addReference(parsed, AplusReferenceImage.SUPPLEMENTARY, url,
                            "Module-specific supporting image.");
                }
            }
        }
        List<AplusReferenceImage> ordered = new ArrayList<>();
        for (String role : List.of(AplusReferenceImage.PRODUCT_TRUTH, AplusReferenceImage.LAYOUT,
                AplusReferenceImage.DETAIL, AplusReferenceImage.FABRIC, AplusReferenceImage.SIZE,
                AplusReferenceImage.SCENE, AplusReferenceImage.STYLE_ANCHOR, AplusReferenceImage.SUPPLEMENTARY)) {
            parsed.stream().filter(item -> item != null && role.equalsIgnoreCase(item.getRole())
                    && item.getUrl() != null && !item.getUrl().isBlank()).forEach(ordered::add);
        }
        parsed.stream().filter(item -> item != null && item.getUrl() != null && !item.getUrl().isBlank()
                && !ordered.contains(item)).forEach(ordered::add);
        return ordered;
    }

    private void addReference(List<AplusReferenceImage> references, String role, String url, String note) {
        if (url == null || url.isBlank()) return;
        AplusReferenceImage reference = new AplusReferenceImage();
        reference.setRole(role);
        reference.setUrl(url.trim());
        reference.setNote(note);
        references.add(reference);
    }

    private String firstReferenceUrl(List<AplusReferenceImage> references, String role) {
        return references.stream()
                .filter(reference -> role.equalsIgnoreCase(reference.getRole()))
                .map(AplusReferenceImage::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse("");
    }

    private String supportingReferenceUrls(List<AplusReferenceImage> references) {
        return references.stream()
                .filter(reference -> !AplusReferenceImage.PRODUCT_TRUTH.equalsIgnoreCase(reference.getRole()))
                .map(AplusReferenceImage::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String effectiveModel(AplusImageTask task) {
        if (task.getModel() != null && !task.getModel().isBlank()) {
            return task.getModel().trim();
        }
        return defaultModel;
    }

    private String effectiveResolution(AplusImageTask task) {
        if (task.getResolution() == null || task.getResolution().isBlank()) {
            return "2K";
        }
        return task.getResolution().trim().toUpperCase();
    }

    private String effectiveAspectRatio(AplusImageTask task) {
        if (task.getAspectRatio() == null || task.getAspectRatio().isBlank()) {
            return "21:9";
        }
        return task.getAspectRatio().trim();
    }

    private String extractOssUrl(String ossResult) {
        if (ossResult == null || ossResult.isBlank()) {
            return null;
        }
        int separator = ossResult.indexOf('|');
        return separator >= 0 ? ossResult.substring(0, separator) : ossResult;
    }
}
