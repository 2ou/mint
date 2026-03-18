package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import com.ai.repository.ImageTaskRepository;
import com.ai.service.ImageTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class ImageTaskServiceImpl implements ImageTaskService {

    private final ImageTaskRepository imageTaskRepository;
    private final KieClientService kieClientService;
    private final OssService ossService;

    @Autowired
    private AppProperties appProperties;

    @Value("${app.local-save-root:D:/AiResult}")
    private String localSaveRoot;

    // 🔴 公用的 OkHttp 客户端
    private final okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    // 🔴 专门用于兜底下载的客户端 (给足 3 分钟超时时间)
    private final okhttp3.OkHttpClient compensationClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build();

    // 🔴 带“限流阀”的专用多线程池，最多同时压缩 3 张图，防止服务器内存溢出！
    private final ExecutorService compressThreadPool = Executors.newFixedThreadPool(3);

    public ImageTaskServiceImpl(ImageTaskRepository imageTaskRepository, KieClientService kieClientService, OssService ossService) {
        this.imageTaskRepository = imageTaskRepository;
        this.kieClientService = kieClientService;
        this.ossService = ossService;
    }

    // ======================== 核心业务逻辑 ========================

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

        if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return new TaskCreateResponse(task);
        }

        try {
            log.info("【步骤1】开始向 KIE 请求任务状态...");
            String tempAiUrl = kieClientService.getResultUrl(task.getTaskId());

            if (tempAiUrl != null && !tempAiUrl.isEmpty()) {
                log.info("【步骤2】成功解析到临时链接: {}", tempAiUrl);
                task.setResultTempUrl(tempAiUrl);
                task.setStatus("SUCCESS");
                task.setErrorMessage(null);

                try {
                    log.info("【步骤3】准备抓取并转存到自有 OSS...");
                    String permanentOssUrl = ossService.uploadResultToOss(task.getSpu(), tempAiUrl);
                    task.setResultOssUrl(permanentOssUrl);
                    task.setLocalPath(null);
                    log.info("【步骤4】OSS 转存成功，永久链接: {}", permanentOssUrl);
                } catch (Exception subEx) {
                    log.error("【步骤4-异常】转存自有 OSS 失败: {}", subEx.getMessage());
                    task.setResultOssUrl(null);
                }

                log.info("【步骤5】准备保存进 MySQL 数据库...");
                imageTaskRepository.save(task);
                log.info("【步骤6】🎉 数据库保存成功！任务流转闭环。");
            } else {
                task.setStatus("PROCESSING");
                imageTaskRepository.save(task);
            }
        } catch (Throwable e) {
            log.error("【严重异常】刷新任务 ID: {} 发生奔溃: {}", id, e.getMessage(), e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            try {
                imageTaskRepository.save(task);
            } catch (Exception saveEx) {
                log.error("【灾难异常】连写入失败状态都报错了: {}", saveEx.getMessage());
            }
        }
        return new TaskCreateResponse(task);
    }


    // ======================== 下载与查询逻辑 ========================

    @Override
    public void batchDownloadZip(List<Long> ids, jakarta.servlet.http.HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=AI_tasks_results.zip");

        ExecutorService executor = Executors.newFixedThreadPool(10);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            List<CompletableFuture<File>> futures = new ArrayList<>();
            List<String> fileNames = new ArrayList<>();

            for (Long id : ids) {
                imageTaskRepository.findById(id).ifPresent(task -> {
                    String imageUrl = task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl();
                    if (imageUrl == null || imageUrl.isEmpty()) return;

                    String ext = imageUrl.toLowerCase().contains(".jpg") || imageUrl.toLowerCase().contains(".jpeg") ? ".jpg" : ".png";
                    String fileName = task.getSpu() + "_" + task.getId() + "_net" + ext;

                    fileNames.add(fileName);

                    CompletableFuture<File> future = CompletableFuture.supplyAsync(() -> {
                        File tempFile = null;
                        try {
                            tempFile = File.createTempFile("zip_dl_", ext);
                            okhttp3.Request request = new okhttp3.Request.Builder()
                                    .url(imageUrl)
                                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                                    .build();

                            try (okhttp3.Response okResponse = httpClient.newCall(request).execute()) {
                                if (!okResponse.isSuccessful() || okResponse.body() == null) {
                                    throw new RuntimeException("HTTP 请求失败: " + okResponse.code());
                                }
                                try (InputStream is = okResponse.body().byteStream();
                                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                                    byte[] buffer = new byte[8192];
                                    int length;
                                    while ((length = is.read(buffer)) > 0) {
                                        fos.write(buffer, 0, length);
                                    }
                                }
                            }
                            return tempFile;
                        } catch (Exception e) {
                            log.error("ZIP并发下载单图失败, 任务ID: {}, 链接: {}", task.getId(), imageUrl, e);
                            if (tempFile != null && tempFile.exists()) tempFile.delete();
                            return null;
                        }
                    }, executor);

                    futures.add(future);
                });
            }

            boolean hasFiles = false;
            for (int i = 0; i < futures.size(); i++) {
                File tempDownloadedFile = futures.get(i).join();

                if (tempDownloadedFile != null && tempDownloadedFile.exists()) {
                    try {
                        zos.putNextEntry(new ZipEntry(fileNames.get(i)));
                        try (FileInputStream fis = new FileInputStream(tempDownloadedFile)) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }
                        }
                        zos.closeEntry();
                        hasFiles = true;
                    } finally {
                        tempDownloadedFile.delete();
                    }
                }
            }

            if (!hasFiles) {
                zos.putNextEntry(new ZipEntry("下载失败提示.txt"));
                String msg = "您选中的任务尚未生成结果，或者网络拦截导致下载失败，因此没有任何图片。";
                zos.write(msg.getBytes("UTF-8"));
                zos.closeEntry();
            }

        } catch (Exception e) {
            log.error("打包 ZIP 彻底崩溃", e);
            throw new RuntimeException("打包 ZIP 失败: " + e.getMessage(), e);
        } finally {
            executor.shutdown();
        }
    }

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


    // ======================== 后台兜底压缩转存机器人 ========================

    /**
     * 后台专属兜底机器人 (多线程并发版)
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void repairMissingOssTasks() {
        List<ImageTask> tasks = imageTaskRepository.findTop5ByStatusAndResultTempUrlIsNotNullAndResultOssUrlIsNullOrderByIdDesc("SUCCESS");
        if (tasks.isEmpty()) return;

        log.info("【OSS兜底】发现 {} 个未转存任务，启动多线程并发抢救...", tasks.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ImageTask task : tasks) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                File tempRawFile = null;
                File tempCompressedFile = null;
                try {
                    tempRawFile = File.createTempFile("kie_raw_repair_", ".png");
                    tempCompressedFile = File.createTempFile("kie_zip_repair_", ".jpg");

                    // 下载原图
                    okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(task.getResultTempUrl())
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                            .build();

                    try (okhttp3.Response response = compensationClient.newCall(request).execute()) {
                        if (!response.isSuccessful() || response.body() == null) {
                            log.warn("【OSS兜底】任务 {} 链接失效，跳过", task.getId());
                            return;
                        }
                        try (InputStream is = response.body().byteStream();
                             FileOutputStream fos = new FileOutputStream(tempRawFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }

                    // 🔴 智能动态压缩：目标体积约 5MB (5 * 1024 * 1024 bytes)
                    long targetSize = 5 * 1024 * 1024L;
                    float quality = 0.9f; // 从 90% 的极高画质起步

                    // 最多进行 3 次试探，防止无意义的 CPU 消耗
                    for (int i = 0; i < 3; i++) {
                        net.coobird.thumbnailator.Thumbnails.of(tempRawFile)
                                .scale(1.0) // 保持 4K 原分辨率不缩水
                                .outputQuality(quality)
                                .outputFormat("jpg") // 转 JPG 剥离透明通道，减小体积
                                .toFile(tempCompressedFile);

                        long currentSize = tempCompressedFile.length();
                        if (currentSize <= targetSize) {
                            log.info("【OSS兜底】第 {} 次压缩达标！当前体积: {} MB, 画质参数: {}",
                                    i + 1, String.format("%.2f", currentSize / 1048576.0), quality);
                            break; // 满足 5MB 以下，直接跳出循环！
                        }

                        // 如果还大于 5MB，每次将画质降低 15% 继续尝试
                        quality -= 0.15f;
                    }

                    // 🔴 并发上传 OSS (完美兼容配置和暴露的 ossClient)
                    AppProperties.Oss oss = appProperties.getOss();
                    String objectName = task.getSpu() + "/result/AI_" + System.currentTimeMillis() + "_zip.jpg";

                    ossService.getOssClient().putObject(oss.getResultBucket(), objectName, tempCompressedFile);

                    String finalOssUrl = oss.getResultPublicHost() + "/" + objectName;

                    // 更新数据库
                    task.setResultOssUrl(finalOssUrl);
                    imageTaskRepository.save(task);
                    log.info("【OSS兜底】🎉 任务 {} 抢救成功！已重新挂载至 OSS", task.getId());

                } catch (Exception e) {
                    log.error("【OSS兜底】❌ 任务 {} 抢救失败: {}", task.getId(), e.getMessage());
                } finally {
                    if (tempRawFile != null && tempRawFile.exists()) tempRawFile.delete();
                    if (tempCompressedFile != null && tempCompressedFile.exists()) tempCompressedFile.delete();
                }

            }, compressThreadPool);

            futures.add(future);
        }

        try {
            // 阻塞主线程，直到这批任务全部被线程池消化完
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("【OSS兜底】✅ 本轮多线程抢救执行完毕！");
        } catch (Exception e) {
            log.error("【OSS兜底】等待多线程执行异常", e);
        }
    }
}