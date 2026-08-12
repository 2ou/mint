package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.AplusCopyUpdateRequest;
import com.ai.dto.AplusImageTaskResponse;
import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusProjectResponse;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusTaskStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.service.AplusCopyService;
import com.ai.service.AplusImageService;
import com.ai.service.AplusProjectService;
import com.ai.service.CanvasTaskService;
import com.ai.service.ImageTaskService;
import com.ai.service.ModelLibraryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/aplus")
@RequiredArgsConstructor
@CrossOrigin
public class AplusController {

    private final AplusProjectService projectService;
    private final AplusCopyService copyService;
    private final AplusImageService imageService;
    private final ImageTaskService imageTaskService;
    private final ModelLibraryService modelLibraryService;
    private final CanvasTaskService canvasTaskService;
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    private final Environment environment;

    @Resource(name = "aplusAsyncExecutor")
    private Executor aplusAsyncExecutor;

    @PostMapping("/projects")
    public ApiResponse<AplusProjectResponse> createProject(
            @RequestBody AplusProjectCreateRequest request,
            HttpServletRequest httpRequest) {
        String operator = (String) httpRequest.getAttribute("operator");
        String shopName = (String) httpRequest.getAttribute("shopName");
        AplusProjectResponse response = projectService.createProject(request, operator, shopName);
        return ApiResponse.ok("A+ 项目已创建，文案生成中", response);
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<AplusProjectResponse> getProject(@PathVariable("id") Long id) {
        return ApiResponse.ok("ok", projectService.getProjectById(id));
    }

    @GetMapping("/projects")
    public ApiResponse<org.springframework.data.domain.Page<AplusProjectResponse>> getProjectPage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "spu", required = false) String spu,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok("ok", projectService.getProjectPage(page, size, spu, status));
    }

    @PostMapping("/projects/{id}/generate-copy")
    public ApiResponse<String> generateCopy(@PathVariable("id") Long id) {
        CompletableFuture.runAsync(() -> copyService.generateCopy(id), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] async copy generation failed: projectId={}, error={}", id, ex.getMessage(), ex);
                    return null;
                });
        return ApiResponse.ok("文案生成任务已提交", null);
    }

    @PutMapping("/projects/{id}/copy")
    public ApiResponse<AplusProjectResponse> updateCopy(@PathVariable("id") Long id,
                                                        @RequestBody AplusCopyUpdateRequest request) {
        return ApiResponse.ok("文案已保存", projectService.updateCopy(id, request));
    }

    @PostMapping("/projects/{id}/generate-images")
    public ApiResponse<String> generateImages(@PathVariable("id") Long id) {
        CompletableFuture.runAsync(() -> imageService.generateImages(id), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] async image generation failed: projectId={}, error={}", id, ex.getMessage(), ex);
                    return null;
                });
        return ApiResponse.ok("图片生成任务已提交", null);
    }

    @PostMapping("/projects/{id}/retry-failed")
    public ApiResponse<Integer> retryFailedModules(@PathVariable("id") Long id) {
        int count = imageService.retryFailedModules(id);
        return ApiResponse.ok("失败模块重试任务已提交", count);
    }

    @PostMapping("/projects/{id}/modules/{moduleCode}/regenerate")
    public ApiResponse<String> regenerateModule(@PathVariable("id") Long id,
                                                @PathVariable("moduleCode") String moduleCode) {
        CompletableFuture.runAsync(() -> imageService.regenerateModule(id, moduleCode), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] async module regeneration failed: projectId={}, module={}, error={}",
                            id, moduleCode, ex.getMessage(), ex);
                    return null;
                });
        return ApiResponse.ok(moduleCode + " 重新生成任务已提交", null);
    }

    @GetMapping("/projects/{id}/modules")
    public ApiResponse<List<AplusImageTaskResponse>> getModuleTasks(@PathVariable("id") Long id) {
        List<AplusImageTaskResponse> responses = imageTaskRepository.findByProjectId(id).stream()
                .map(AplusImageTaskResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok("ok", responses);
    }

    @PostMapping("/projects/{id}/download-zip")
    public void downloadZip(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        AplusProject project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + id));

        List<AplusImageTask> successTasks = imageTaskRepository.findByProjectId(id).stream()
                .filter(task -> AplusTaskStatus.SUCCESS.name().equals(task.getStatus())
                        && resolveDownloadUrl(task) != null)
                .toList();

        if (successTasks.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"没有可下载的图片\"}");
            return;
        }

        String zipFileName = "A+_" + project.getProjectName() + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(zipFileName, StandardCharsets.UTF_8) + "\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (AplusImageTask task : successTasks) {
                String imageUrl = resolveDownloadUrl(task);
                String entryName = sanitizeFileName(task.getModuleCode() + "_" + task.getModuleName() + ".jpg");
                try (InputStream is = new URL(imageUrl).openStream()) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                } catch (Exception e) {
                    log.error("[A+] zip entry failed: module={}, error={}", task.getModuleCode(), e.getMessage());
                }
            }
        }
    }

    @GetMapping("/proxy-download")
    public void proxyDownload(@RequestParam String url, @RequestParam String filename,
                              HttpServletResponse response) throws IOException {
        String cleanUrl = cleanUrl(url);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");

        try (InputStream is = new URL(cleanUrl).openStream();
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
        }
    }

    /**
     * KIE 统一回调接口（所有模块共用）
     * 回调顺序：A+ 套图 → 普通生图 → 模特库生图
     */
    @PostMapping("/callback")
    public ApiResponse<String> kieCallback(@RequestBody Map<String, Object> payload) {
        log.info("[Callback] KIE callback received: {}", payload);
        String taskId = extractKieTaskId(payload);
        if (taskId.isBlank()) {
            log.warn("[Callback] missing taskId");
            return ApiResponse.ok("missing taskId", null);
        }
        try {
            // 按优先级依次尝试：A+ → 普通生图 → 模特库 → AI 画布
            if (imageTaskService.refreshTaskByKieTaskId(taskId)) {
                log.info("[Callback] handled by ImageTask: {}", taskId);
            } else if (modelLibraryService.refreshTaskByKieTaskId(taskId)) {
                log.info("[Callback] handled by ModelLibrary: {}", taskId);
            } else if (canvasTaskService.refreshTaskByCallback(payload)) {
                log.info("[Callback] handled by AiCanvas: {}", taskId);
            } else {
                log.warn("[Callback] taskId not found in any service: {}", taskId);
            }
        } catch (Exception e) {
            log.error("[Callback]处理失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return ApiResponse.ok("ok", null);
    }

    private String extractKieTaskId(Map<String, Object> payload) {
        if (payload == null) return "";
        String taskId = firstNonBlank(
                textValue(payload.get("taskId")),
                textValue(payload.get("task_id")),
                textValue(payload.get("id"))
        );
        if (!taskId.isBlank()) return taskId;
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return firstNonBlank(
                    textValue(dataMap.get("taskId")),
                    textValue(dataMap.get("task_id")),
                    textValue(dataMap.get("id"))
            );
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<String> deleteProject(@PathVariable("id") Long id) {
        projectService.deleteProject(id);
        return ApiResponse.ok("项目删除成功", null);
    }

    private String cleanUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int separator = url.indexOf('|');
        return separator >= 0 ? url.substring(0, separator) : url;
    }

    /** 本地环境（dev profile）判定：本地轮询已将 resultOssUrl 改写为相对路径 /ai-result/...，批量下载需改走 KIE 临时地址 */
    private boolean isLocalEnv() {
        return environment.acceptsProfiles(Profiles.of("dev"));
    }

    /** 解析批量下载应抓取的图片地址：本地环境优先用 KIE 临时文件地址（绝对 URL），生产环境优先用 resultOssUrl（绝对 OSS 地址） */
    private String resolveDownloadUrl(AplusImageTask task) {
        if (isLocalEnv()) {
            String temp = cleanUrl(task.getResultTempUrl());
            return temp != null ? temp : cleanUrl(task.getResultOssUrl());
        }
        String oss = cleanUrl(task.getResultOssUrl());
        return oss != null ? oss : cleanUrl(task.getResultTempUrl());
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
