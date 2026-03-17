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
import org.springframework.scheduling.annotation.Scheduled;
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

    @Value("${app.local-save-root:D:/AiResult}")
    private String localSaveRoot;

    public ImageTaskServiceImpl(ImageTaskRepository imageTaskRepository, KieClientService kieClientService, OssService ossService) {
        this.imageTaskRepository = imageTaskRepository;
        this.kieClientService = kieClientService;
        this.ossService = ossService;
    }

    // 🔴 核心：5分钟执行一次定时任务 (300000 毫秒)
    @Scheduled(fixedRate = 300000)
    public void scheduledRefreshProcessingTasks() {
        log.info("【定时任务】开始自动刷新处理中的 AI 任务...");
        List<ImageTask> processingTasks = imageTaskRepository.findAll((root, query, cb) -> cb.or(
                cb.equal(root.get("status"), "PROCESSING"),
                cb.equal(root.get("status"), "GENERATING")
        ));

        if (processingTasks.isEmpty()) {
            log.info("【定时任务】当前没有处理中的任务。");
            return;
        }

        for (ImageTask task : processingTasks) {
            try {
                refreshTask(task.getId());
            } catch (Exception e) {
                log.error("【定时任务】刷新任务 ID: {} 失败", task.getId(), e);
            }
        }
        log.info("【定时任务】自动刷新完成，共处理 {} 个任务。", processingTasks.size());
    }

    @Override
    public TaskCreateResponse createWithUrl(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl, Integer taskType, String operator, String shopName) {
        ImageTask task = new ImageTask();
        task.setSpu(spu); task.setPrompt(prompt); task.setResolution(resolution); task.setModel(model);
        task.setInputImageUrl(inputUrl); task.setColorImageUrl(colorUrl); task.setStatus("CREATED");
        task.setTaskType(taskType != null ? taskType : 1);

        // 🔴 落库归属店铺和操作人
        task.setOperator(operator);
        task.setShopName(shopName);

        try {
            String taskId = kieClientService.createTask(spu, prompt, resolution, model, inputUrl, colorUrl);
            task.setTaskId(taskId); task.setStatus("PROCESSING");
        } catch (Exception e) {
            log.error("创建 KIE 任务失败", e);
            task.setStatus("FAILED");
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            task.setErrorMessage(errorMsg.length() > 2000 ? errorMsg.substring(0, 1990) + "..." : errorMsg);
        }
        return new TaskCreateResponse(imageTaskRepository.save(task));
    }

    @Override
    public TaskCreateResponse refreshTask(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("未找到该任务 ID: " + id));

        // 如果任务已经终结，不需要重复同步
        if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return new TaskCreateResponse(task);
        }

        try {
            // 1. 获取 AI 厂商的临时结果链接
            String tempAiUrl = kieClientService.getResultUrl(task.getTaskId());

            if (tempAiUrl != null && !tempAiUrl.isEmpty()) {
                task.setResultTempUrl(tempAiUrl);
                task.setStatus("SUCCESS");
                task.setErrorMessage(null);

                try {
                    // 2. 核心：将图片转存到您自己的阿里云 OSS (永久链接)
                    // 不再调用 saveResultToLocal，彻底取消本地下载逻辑
                    String permanentOssUrl = ossService.uploadResultToOss(task.getSpu(), tempAiUrl);
                    task.setResultOssUrl(permanentOssUrl);

                    // 清除本地路径记录
                    task.setLocalPath(null);
                } catch (Exception subEx) {
                    log.error("转存自有 OSS 失败: {}", subEx.getMessage());
                    // 转存失败则保底使用 AI 临时链接，确保前端能看到图
                    task.setResultOssUrl(tempAiUrl);
                }

                imageTaskRepository.save(task);
            } else {
                task.setStatus("PROCESSING");
                imageTaskRepository.save(task);
            }
        } catch (Exception e) {
            log.error("刷新任务异常", e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
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
                    String localPath = task.getLocalPath();
                    if (localPath != null && !localPath.isEmpty() && new File(localPath).exists()) {
                        try (InputStream is = new FileInputStream(new File(localPath))) {
                            zos.putNextEntry(new ZipEntry(task.getSpu() + "_" + task.getId() + ".png"));
                            byte[] buffer = new byte[8192]; int length;
                            while ((length = is.read(buffer)) > 0) zos.write(buffer, 0, length);
                            zos.closeEntry(); return;
                        } catch (Exception e) { log.error("从本地硬盘读取打包失败: " + localPath, e); }
                    }
                    String imageUrl = task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl();
                    if (imageUrl == null || imageUrl.isEmpty()) return;
                    try {
                        HttpURLConnection conn = (HttpURLConnection) new URL(imageUrl).openConnection();
                        conn.setRequestMethod("GET"); conn.setConnectTimeout(5000); conn.setReadTimeout(30000);
                        try (InputStream is = conn.getInputStream()) {
                            zos.putNextEntry(new ZipEntry(task.getSpu() + "_" + task.getId() + "_net.png"));
                            byte[] buffer = new byte[8192]; int length;
                            while ((length = is.read(buffer)) > 0) zos.write(buffer, 0, length);
                            zos.closeEntry();
                        }
                    } catch (Exception e) { log.error("网络下载兜底打包失败: " + imageUrl, e); }
                });
            }
        } catch (Exception e) { throw new RuntimeException("打包 ZIP 失败", e); }
    }

    // 🔴 核心：重构分页查询逻辑，加入 taskType 筛选
    @Override
    public Page<TaskCreateResponse> getTaskPage(int page, int size, String spu, String status, Integer taskType, LocalDateTime startTime, LocalDateTime endTime) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<ImageTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (spu != null && !spu.trim().isEmpty()) predicates.add(cb.like(root.get("spu"), "%" + spu.trim() + "%"));
            if (status != null && !status.trim().isEmpty()) predicates.add(cb.equal(root.get("status"), status.trim()));
            if (taskType != null) predicates.add(cb.equal(root.get("taskType"), taskType));
            if (startTime != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            if (endTime != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return imageTaskRepository.findAll(spec, pageable).map(TaskCreateResponse::new);
    }

    @Override
    public List<TaskCreateResponse> listAllTasks() {
        return imageTaskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(TaskCreateResponse::new).collect(Collectors.toList());
    }

    @Override
    public TaskCreateResponse getTaskById(Long id) {
        return new TaskCreateResponse(imageTaskRepository.findById(id).orElseThrow(() -> new RuntimeException("该任务不存在")));
    }

    @Override
    public void downloadTaskFile(Long id) { throw new RuntimeException("此接口已停用，请使用批量下载功能 (ZIP)"); }
}