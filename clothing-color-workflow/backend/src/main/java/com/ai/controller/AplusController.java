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
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;

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
    public ApiResponse<AplusProjectResponse> getProject(@PathVariable Long id) {
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
    public ApiResponse<String> generateCopy(@PathVariable Long id) {
        CompletableFuture.runAsync(() -> copyService.generateCopy(id), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] async copy generation failed: projectId={}, error={}", id, ex.getMessage(), ex);
                    return null;
                });
        return ApiResponse.ok("文案生成任务已提交", null);
    }

    @PutMapping("/projects/{id}/copy")
    public ApiResponse<AplusProjectResponse> updateCopy(@PathVariable Long id,
                                                        @RequestBody AplusCopyUpdateRequest request) {
        return ApiResponse.ok("文案已保存", projectService.updateCopy(id, request));
    }

    @PostMapping("/projects/{id}/generate-images")
    public ApiResponse<String> generateImages(@PathVariable Long id) {
        CompletableFuture.runAsync(() -> imageService.generateImages(id), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] async image generation failed: projectId={}, error={}", id, ex.getMessage(), ex);
                    return null;
                });
        return ApiResponse.ok("图片生成任务已提交", null);
    }

    @PostMapping("/projects/{id}/retry-failed")
    public ApiResponse<Integer> retryFailedModules(@PathVariable Long id) {
        int count = imageService.retryFailedModules(id);
        return ApiResponse.ok("失败模块重试任务已提交", count);
    }

    @PostMapping("/projects/{id}/modules/{moduleCode}/regenerate")
    public ApiResponse<String> regenerateModule(@PathVariable Long id, @PathVariable String moduleCode) {
        CompletableFuture.runAsync(() -> imageService.regenerateModule(id, moduleCode), aplusAsyncExecutor)
                .exceptionally(ex -> {
                    log.error("[A+] async module regeneration failed: projectId={}, module={}, error={}",
                            id, moduleCode, ex.getMessage(), ex);
                    return null;
                });
        return ApiResponse.ok(moduleCode + " 重新生成任务已提交", null);
    }

    @GetMapping("/projects/{id}/modules")
    public ApiResponse<List<AplusImageTaskResponse>> getModuleTasks(@PathVariable Long id) {
        List<AplusImageTaskResponse> responses = imageTaskRepository.findByProjectId(id).stream()
                .map(AplusImageTaskResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok("ok", responses);
    }

    @PostMapping("/projects/{id}/download-zip")
    public void downloadZip(@PathVariable Long id, HttpServletResponse response) throws IOException {
        AplusProject project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + id));

        List<AplusImageTask> successTasks = imageTaskRepository.findByProjectId(id).stream()
                .filter(task -> AplusTaskStatus.SUCCESS.name().equals(task.getStatus())
                        && cleanUrl(task.getResultOssUrl()) != null)
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
                String imageUrl = cleanUrl(task.getResultOssUrl());
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

    @DeleteMapping("/projects/{id}")
    public ApiResponse<String> deleteProject(@PathVariable Long id) {
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

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
