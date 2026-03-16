package com.ai.service.impl;

import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import com.ai.repository.ImageTaskRepository;
import com.ai.service.ImageTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageTaskServiceImpl implements ImageTaskService {

    private final ImageTaskRepository imageTaskRepository;
    private final KieClientService kieClientService;
    private final OssService ossService;

    @Value("${app.wait-result-ms:5000}")
    private long waitResultMs;

    @Value("${app.local-save-root}")
    private String localSaveRoot;

    @Override
    public TaskCreateResponse refreshTask(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到该任务 ID: " + id));

        // 如果任务已经终结，不需要再去骚扰 KIE
        if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return new TaskCreateResponse(task);
        }

        try {
            // 1. 去 KIE 查询最新结果
            String resultUrl = kieClientService.getResultUrl(task.getTaskId());

            if (resultUrl != null && !resultUrl.isEmpty()) {
                // 🔴 核心优化一：落袋为安！拿到 KIE 的图立刻存入数据库！
                task.setResultTempUrl(resultUrl);
                task.setStatus("SUCCESS");
                task.setErrorMessage(null); // 抹除旧的报错信息
                task = imageTaskRepository.save(task); // 执行首次保存

                // 🔴 核心优化二：把转存 OSS 和本地另起一个 try-catch (降级处理)
                try {
                    String resultOssUrl = ossService.uploadResultToOss(task.getSpu(), resultUrl);
                    task.setResultOssUrl(resultOssUrl);

                    String localPath = ossService.saveResultToLocal(task.getSpu(), resultUrl, localSaveRoot);
                    task.setLocalPath(localPath);

                    // 转存成功，再次更新数据库补齐 OSS 链接
                    imageTaskRepository.save(task);
                } catch (Exception subEx) {
                    log.warn("任务 {} KIE出图成功，但转存失败: {}", task.getId(), subEx.getMessage());
                    // 记录备注，但绝不把 SUCCESS 改回 FAILED
                    task.setErrorMessage("图片获取成功，但转存到你自己的OSS失败: " + subEx.getMessage());
                    imageTaskRepository.save(task);
                }
            } else {
                // 没查到链接，说明还在画，保持 PROCESSING 状态
                task.setStatus("PROCESSING");
                imageTaskRepository.save(task);
            }
        } catch (Exception e) {
            log.error("刷新任务 ID: {} 发生全局异常", id, e);
            task.setStatus("FAILED");

            // 防止第三方报错信息过长，导致存入 MySQL 时发生 Data Truncation 报错崩溃
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
    public TaskCreateResponse getTaskById(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return new TaskCreateResponse(task);
    }

    @Override
    public List<TaskCreateResponse> listAllTasks() {
        List<TaskCreateResponse> list = new ArrayList<>();
        for (ImageTask task : imageTaskRepository.findAll()) {
            list.add(new TaskCreateResponse(task));
        }
        return list;
    }

    @Override
    public void downloadTaskFile(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        ossService.saveResultToLocal(task.getSpu(), task.getResultTempUrl(), localSaveRoot);
    }

    @Override
    public TaskCreateResponse createWithUrl(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl) {
        // 1. 初始化并持久化任务记录到 MySQL
        ImageTask task = new ImageTask();
        task.setSpu(spu);
        task.setPrompt(prompt);
        task.setResolution(resolution);
        task.setModel(model); // 记录前端指定使用的 AI 模型
        task.setInputImageUrl(inputUrl);
        task.setColorImageUrl(colorUrl);
        task.setStatus("CREATED");
        imageTaskRepository.save(task);

        try {
            // 2. 调用远端 AI 接口投递任务
            String taskId = kieClientService.createTask(spu, prompt, resolution, model, inputUrl, colorUrl);

            // 3. 投递成功，更新远端 taskId 和状态 (不阻塞等待出图)
            task.setTaskId(taskId);
            task.setStatus("PROCESSING");
        } catch (Exception e) {
            // 投递异常时，记录失败状态和错误信息
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
        }

        // 4. 更新数据库状态并返回
        imageTaskRepository.save(task);
        return new TaskCreateResponse(task);
    }



    // ... 在 ImageTaskServiceImpl 中实现方法
    @Override
    public void batchDownloadZip(List<Long> ids, jakarta.servlet.http.HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=AI_tasks_results.zip");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Long id : ids) {
                imageTaskRepository.findById(id).ifPresent(task -> {

                    // 🔴 步骤 1：最高优先级，直接从服务器的“本地物理硬盘”读取，速度是网络的 100 倍！
                    String localPath = task.getLocalPath();
                    if (localPath != null && !localPath.isEmpty()) {
                        File localFile = new File(localPath);
                        if (localFile.exists()) {
                            try (InputStream is = new FileInputStream(localFile)) {
                                String fileName = task.getSpu() + "_" + task.getId() + ".png";
                                zos.putNextEntry(new ZipEntry(fileName));
                                byte[] buffer = new byte[8192]; // 加大缓冲区，8KB 提升写入速度
                                int length;
                                while ((length = is.read(buffer)) > 0) {
                                    zos.write(buffer, 0, length);
                                }
                                zos.closeEntry();
                                return; // ⚡ 本地读取成功，直接跳过后面的网络下载步骤，进入下一张图
                            } catch (Exception e) {
                                log.error("从本地硬盘读取打包失败，将尝试网络兜底: " + localPath, e);
                            }
                        }
                    }

                    // 🔴 步骤 2：如果本地文件被误删了，再去请求网络链接（优先使用你的阿里云 OSS 链接，速度比临时外网链接快得多）
                    String imageUrl = task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl();
                    if (imageUrl == null || imageUrl.isEmpty()) return;

                    try {
                        URL url = new URL(imageUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(30000); // ❗️ 关键：读取超时放宽到 30 秒，防止 4K 大图下载一半断开

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

    @Scheduled(fixedDelay = 10000)
    public void autoPollTaskResults() {
        // 1. 去数据库捞出所有状态为 GENERATING 或 PROCESSING 的任务
        List<ImageTask> processingTasks = imageTaskRepository.findByStatusIn(Arrays.asList("GENERATING", "PROCESSING"));

        if (processingTasks.isEmpty()) {
            return; // 没有处理中的任务就直接返回，不消耗资源
        }

        log.info("开始轮询任务结果，当前有 {} 个任务正在生成中...", processingTasks.size());

        for (ImageTask task : processingTasks) {
            try {
                // 调用你已经写好的刷新逻辑 (它会去查 KIE，查到了会自动转存 OSS 并更新数据库状态为 SUCCESS)
                refreshTask(task.getId());
            } catch (Exception e) {
                log.error("轮询任务 ID: {} 发生异常: {}", task.getId(), e.getMessage());
            }
        }
    }
}