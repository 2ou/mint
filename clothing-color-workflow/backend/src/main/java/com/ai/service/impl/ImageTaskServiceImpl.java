package com.ai.service.impl;

import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import com.ai.repository.ImageTaskRepository;
import com.ai.service.ImageTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class ImageTaskServiceImpl implements ImageTaskService {

    private final ImageTaskRepository imageTaskRepository;
    private final KieClientService kieClientService;
    private final OssService ossService;

    // 读取 application.yml 中配置的本地保存路径，默认 D:/AiResult
    @Value("${app.local-save-root:D:/AiResult}")
    private String localSaveRoot;

    public ImageTaskServiceImpl(ImageTaskRepository imageTaskRepository,
                                KieClientService kieClientService,
                                OssService ossService) {
        this.imageTaskRepository = imageTaskRepository;
        this.kieClientService = kieClientService;
        this.ossService = ossService;
    }

    @Override
    public TaskCreateResponse createWithUrl(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl, Integer taskType) {
        ImageTask task = new ImageTask();
        task.setSpu(spu);
        task.setPrompt(prompt);
        task.setResolution(resolution);
        task.setModel(model);
        task.setInputImageUrl(inputUrl);
        task.setColorImageUrl(colorUrl);
        task.setStatus("CREATED");

        // 🔴 核心：保存任务类型，如果前端没传默认存 1 (换色)
        task.setTaskType(taskType != null ? taskType : 1);

        try {
            // 提交给远端 KIE 接口
            String taskId = kieClientService.createTask(spu, prompt, resolution, model, inputUrl, colorUrl);
            task.setTaskId(taskId);
            task.setStatus("PROCESSING");
        } catch (Exception e) {
            log.error("创建 KIE 任务失败", e);
            task.setStatus("FAILED");

            // 防止第三方报错信息过长，导致存入 MySQL 时发生 Data Truncation 崩溃
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (errorMsg.length() > 2000) {
                errorMsg = errorMsg.substring(0, 1990) + "...";
            }
            task.setErrorMessage(errorMsg);
        }

        task = imageTaskRepository.save(task);
        return new TaskCreateResponse(task);
    }

    @Override
    public TaskCreateResponse refreshTask(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到该任务 ID: " + id));

        // 如果任务已经终结，不需要再去骚扰 KIE
        if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return new TaskCreateResponse(task);
        }

        try {
            // 去 KIE 查询最新结果
            String resultUrl = kieClientService.getResultUrl(task.getTaskId());

            if (resultUrl != null && !resultUrl.isEmpty()) {
                // 拿到 KIE 的图立刻更新状态
                task.setResultTempUrl(resultUrl);
                task.setStatus("SUCCESS");
                task.setErrorMessage(null);

                // 🔴 核心优化：不再传 OSS，直接下载到本地硬盘并生成本地图片服务的 URL
                try {
                    String localPath = ossService.saveResultToLocal(task.getSpu(), resultUrl, localSaveRoot);
                    task.setLocalPath(localPath);

                    File file = new File(localPath);
                    String localServerUrl = "http://localhost:8080/ai-images/" + task.getSpu() + "/" + file.getName();
                    task.setResultOssUrl(localServerUrl);
                } catch (Exception subEx) {
                    log.warn("保存到本地硬盘失败: {}", subEx.getMessage());
                    task.setErrorMessage("图片获取成功，但存入本地硬盘失败: " + subEx.getMessage());
                }
                imageTaskRepository.save(task);
            } else {
                task.setStatus("PROCESSING");
                imageTaskRepository.save(task);
            }
        } catch (Exception e) {
            log.error("刷新任务 ID: {} 发生全局异常", id, e);
            task.setStatus("FAILED");

            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (errorMsg.length() > 2000) {
                errorMsg = errorMsg.substring(0, 1990) + "...";
            }
            task.setErrorMessage(errorMsg);
            imageTaskRepository.save(task);
        }
        return new TaskCreateResponse(task);
    }

    @Override
    public void batchDownloadZip(List<Long> ids, jakarta.servlet.http.HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=AI_tasks_results.zip");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Long id : ids) {
                imageTaskRepository.findById(id).ifPresent(task -> {

                    // 🔴 步骤 1：最高优先级，直接从服务器的“本地物理硬盘”读取，极速秒下！
                    String localPath = task.getLocalPath();
                    if (localPath != null && !localPath.isEmpty()) {
                        File localFile = new File(localPath);
                        if (localFile.exists()) {
                            try (InputStream is = new FileInputStream(localFile)) {
                                String fileName = task.getSpu() + "_" + task.getId() + ".png";
                                zos.putNextEntry(new ZipEntry(fileName));
                                byte[] buffer = new byte[8192]; // 加大缓冲区提升速度
                                int length;
                                while ((length = is.read(buffer)) > 0) {
                                    zos.write(buffer, 0, length);
                                }
                                zos.closeEntry();
                                return; // ⚡ 本地读取成功，直接跳过后面的网络下载步骤
                            } catch (Exception e) {
                                log.error("从本地硬盘读取打包失败，将尝试网络兜底: " + localPath, e);
                            }
                        }
                    }

                    // 🔴 步骤 2：兜底方案，万一本地文件被误删了，再去请求网络链接
                    String imageUrl = task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl();
                    if (imageUrl == null || imageUrl.isEmpty()) return;

                    try {
                        URL url = new URL(imageUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(30000); // 放宽到 30 秒，防止 4K 大图下载一半断开

                        String fileName = task.getSpu() + "_" + task.getId() + "_net.png";
                        try (InputStream is = conn.getInputStream()) {
                            zos.putNextEntry(new ZipEntry(fileName));
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = is.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }
                            zos.closeEntry();
                        }
                    } catch (Exception e) {
                        log.error("网络下载兜底打包失败: " + imageUrl, e);
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException("打包 ZIP 失败", e);
        }
    }

    @Override
    public Page<TaskCreateResponse> getTaskPage(int page, int size, String spu, String status, LocalDateTime startTime, LocalDateTime endTime) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<ImageTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (spu != null && !spu.trim().isEmpty()) {
                predicates.add(cb.like(root.get("spu"), "%" + spu.trim() + "%"));
            }
            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ImageTask> taskPage = imageTaskRepository.findAll(spec, pageable);
        return taskPage.map(TaskCreateResponse::new);
    }

    @Override
    public List<TaskCreateResponse> listAllTasks() {
        // 倒序拉取全量任务，供前端极速筛选使用
        List<ImageTask> tasks = imageTaskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return tasks.stream().map(TaskCreateResponse::new).collect(Collectors.toList());
    }

    @Override
    public TaskCreateResponse getTaskById(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("该任务不存在"));
        return new TaskCreateResponse(task);
    }

    @Override
    public void downloadTaskFile(Long id) {
        // 老版本的单张下载接口，通常已经被批量下载替代
        throw new RuntimeException("此接口已停用，请使用批量下载功能 (ZIP)");
    }
}