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
import com.ai.config.AppProperties;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final AppProperties appProperties;

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
            String callbackUrl = appProperties.getKie().getCallbackUrl();
            String kieTaskId = kieClientService.createTask(
                    project.getSpu(),
                    prompt,
                    resolution,
                    task.getAspectRatio() != null ? task.getAspectRatio() : "16:9",
                    imageModel,
                    project.getReferenceImageUrl(),
                    task.getSupplementaryImageUrl(),
                    callbackUrl
            );

            task.setPrompt(prompt);
            task.setKieTaskId(kieTaskId);
            task.setStatus(AplusTaskStatus.PROCESSING.name());
            task.setModel(imageModel);
            task.setResolution(resolution);
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
                try {
                    // 🔴 本地轮询结果落本地 D:/AiResult，不再上 OSS
                    String localPath = ossService.downloadResultToLocal("aplus", String.valueOf(task.getId()), result.getResultUrl());
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
                "Technical layout: front product view, measurement arrows, compact size chart panel. " +
                "If size data provided, render exact table (Size/Bust/Length/Sleeve). Otherwise use fit labels, no fake numbers.");
        MODULE_TEXT_GUIDES.put("AD-06",
                "Size chart with exact supplied values, or fit labels if no measurements provided.");

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
        boolean hasRef = hasAplusReference(project);
        String moduleLayout = MODULE_LAYOUTS.getOrDefault(code, "Premium e-commerce A+ module layout.");
        String moduleTextGuide = MODULE_TEXT_GUIDES.getOrDefault(code, "Short headline + 2-3 product-benefit labels.");
        boolean modelScene = MODEL_SCENE_MODULES.contains(code);

        StringBuilder sb = new StringBuilder(2048);
        // ── 任务概述 ──
        sb.append("Create one production-ready 16:9 A+ module image for women's fashion e-commerce.\n");
        sb.append(hasRef
                ? "Image-to-image: A+ reference provided for layout style only, not product truth.\n\n"
                : "No A+ reference. Build from selling points, module copy, and supplementary images.\n\n");

        // ── 模块 & 产品 ──
        sb.append("Module: ").append(code).append(" ").append(task.getModuleName()).append("\n");
        sb.append("SPU: ").append(project.getSpu()).append("\n");
        sb.append("A+ Reference: ").append(hasRef ? project.getReferenceImageUrl() : "Not provided").append("\n\n");

        // ── Image Input 角色 ──
        appendImageInputRoleMap(sb, task, project);

        // ── 参考图使用规则 ──
        if (hasRef) {
            sb.append("A+ Reference Rules: layout/hierarchy/panel-rhythm/crop/text-placement only. " +
                    "Do NOT copy its product, model, face, pose, brand, logo, or scene. " +
                    "Replace product with user's product from SPU/selling-points/module-copy/supplementary images.\n\n");
        }

        // ── 产品构建 + 生成模式 ──
        sb.append("Product: match SPU, selling points, module copy. No invented logos/prints/hardware/category changes. " +
                "Product clarity > scene creativity.\n");
        sb.append(modelScene
                ? "Mode: realistic American model + authentic lifestyle scene. Dress in user's product. " +
                  "Model: Curve/Plus-Size or Commercial/Catalog woman, 30-42, natural skin, body-positive. " +
                  "Scene: modern apartment, coffee shop, casual office, NYC/LA street, garden/patio. " +
                  "Minimal accessories.\n\n"
                : "Mode: product-focused (cutout/flat-lay/folded/fabric macro/detail insets/size chart/care still-life). " +
                  "No new model or scene unless user instructions request it.\n\n");

        // ── 风格锚点 + 布局 ──
        sb.append("Style: ").append(AplusModuleDefinition.STYLE_ANCHOR).append("\n\n");
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
        sb.append("Quality: sharp edges, realistic drape/folds, clean lighting, balanced spacing, clear hierarchy, " +
                "consistent palette, safe margins, premium catalog finish.\n\n");

        // ── 负面约束 ──
        sb.append("Negative: ").append(modelScene ? MODEL_SCENE_NEGATIVE : PRODUCT_NEGATIVE).append("\n\n");

        // ── 输出规格 ──
        sb.append("Output: 16:9, ").append(effectiveResolution(task)).append(", single finished image.");
        return sb.toString();
    }

    private void appendImageInputRoleMap(StringBuilder sb, AplusImageTask task, AplusProject project) {
        boolean hasRef = hasAplusReference(project);
        sb.append("Image Inputs:\n");
        sb.append(hasRef
                ? "- [0] A+ reference: layout/style only, not garment identity.\n"
                : "- No A+ reference. Garment identity from SPU/selling-points/module-copy/supplementary images.\n");
        String supplementary = task.getSupplementaryImageUrl();
        if (supplementary != null && !supplementary.isBlank()) {
            String[] urls = supplementary.split(",");
            int start = hasRef ? 1 : 0;
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i] == null ? "" : urls[i].trim();
                if (!url.isBlank()) {
                    sb.append("- [").append(start + i).append("] supplementary: ").append(url)
                            .append(". Module-specific detail only.\n");
                }
            }
        }
        if (project.getLayoutTemplateName() != null && !project.getLayoutTemplateName().isBlank()) {
            sb.append("- Layout template: ").append(project.getLayoutTemplateName()).append(" (structure only).\n");
        }
        sb.append("\n");
    }

    private boolean hasAplusReference(AplusProject project) {
        return project.getReferenceImageUrl() != null && !project.getReferenceImageUrl().isBlank();
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

    private String extractOssUrl(String ossResult) {
        if (ossResult == null || ossResult.isBlank()) {
            return null;
        }
        int separator = ossResult.indexOf('|');
        return separator >= 0 ? ossResult.substring(0, separator) : ossResult;
    }
}
