package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    // 🔴 1. 将后台轮询的限流并发数提升到 10
    private final ExecutorService pollingPool = Executors.newFixedThreadPool(10);

    private final ImageTaskRepository imageTaskRepository;
    private final KieClientService kieClientService;
    private final OssService ossService;

    @Autowired
    private AppProperties appProperties;

    @Value("${app.local-save-root:/data/ai-images/tmp}")
    private String localSaveRoot;

    // 🔴 公用的 OkHttp 客户端
    private final okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public ImageTaskServiceImpl(ImageTaskRepository imageTaskRepository, KieClientService kieClientService, OssService ossService) {
        this.imageTaskRepository = imageTaskRepository;
        this.kieClientService = kieClientService;
        this.ossService = ossService;
    }

    // ======================== 核心业务逻辑 ========================
    @Scheduled(fixedRate = 180000) // 3分钟跑一次
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

        log.info("【定时任务】捞出 {} 个处理中任务，开始并发检测 (最大并发数: 10)...", processingTasks.size());

        // 🔴 核心修复：把普通的 for 循环改成并行派发！把任务扔给 pollingPool
        List<CompletableFuture<Void>> futures = processingTasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    try {
                        refreshTask(task.getId());
                    } catch (Exception e) {
                        log.error("【定时任务】并发刷新任务 ID: {} 发生异常: {}", task.getId(), e.getMessage());
                    }
                }, pollingPool)) // 👈 这里才真正用到了你定义的 10 线程池！
                .collect(Collectors.toList());

        // 🔴 阻塞主定时任务：等待这 10 个护士把活干完（或者超时强杀），再结束本轮定时任务
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("【定时任务】本轮自动刷新完成，共处理 {} 个任务。", processingTasks.size());
    }

    @Override
    public TaskCreateResponse createWithUrl(String spu, String prompt, String resolution, String aspectRatio, String model, String inputUrl, String colorUrl, Integer taskType, String operator, String shopName) {
        ImageTask task = new ImageTask();
        task.setSpu(spu); task.setPrompt(prompt); task.setResolution(resolution); task.setModel(model);
        task.setInputImageUrl(inputUrl); task.setColorImageUrl(colorUrl); task.setStatus("CREATED");
        task.setTaskType(taskType != null ? taskType : 1);
        task.setOperator(operator);
        task.setShopName(shopName);

        try {
            String taskId = kieClientService.createTask(spu, prompt, resolution, aspectRatio, model, inputUrl, colorUrl);
            task.setTaskId(taskId); task.setStatus("PROCESSING");
        } catch (Exception e) {
            log.error("创建 KIE 任务失败", e);
            task.setStatus("FAILED");
            String finalErrorMsg = e.getMessage() != null ? e.getMessage() : "未知异常";

            // 🔴 智能截取：只找 '{' 后面的纯 JSON 部分进行解析
            try {
                int jsonStartIndex = finalErrorMsg.indexOf("{");
                if (jsonStartIndex != -1) {
                    String pureJson = finalErrorMsg.substring(jsonStartIndex);
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(pureJson);

                    // 尝试精准提取 data 里面的 failMsg
                    if (root.has("data") && root.get("data").has("failMsg") && !root.get("data").get("failMsg").isNull()) {
                        finalErrorMsg = root.get("data").get("failMsg").asText();
                    }
                }
            } catch (Exception ignored) {
                // 如果解析失败（比如网络超时没有返回JSON），保持原样，直接存入 finalErrorMsg
            }

            // TEXT 字段无限长，直接存入！
            task.setErrorMessage(finalErrorMsg);
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
            log.info("【步骤1】向 KIE 请求完整结果...");
            KieTaskResult kieResult = kieClientService.getFullResult(task.getTaskId());

            if (kieResult.isSuccess() && kieResult.getResultUrl() != null) {
                log.info("【步骤2】KIE 任务已完成，获取到原始结果链接！");

                // 🔴 核心逻辑 1：立刻赋值保底数据，并修改状态为 SUCCESS
                task.setResultTempUrl(kieResult.getResultUrl());
                task.setStatus("SUCCESS");
                task.setErrorMessage(null); // 清除可能存在的历史错误

                // 转换时间
                if (kieResult.getCompleteTime() != null) {
                    task.setCompleteTime(java.time.Instant.ofEpochMilli(kieResult.getCompleteTime())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime());
                }

                // 🔴 核心逻辑 2：第一段提交！立刻存入数据库保底！
                // 这样就算下一秒服务器断电，这张图的原始链接也已经安全躺在你的数据库里了。
                imageTaskRepository.save(task);
                log.info("【保底成功】任务 {} 已变更为 SUCCESS，原始链接已入库保护！", task.getId());

                // 🔴 核心逻辑 3：把容易报错的转存功能“隔离”起来
                try {
                    log.info("【步骤3】尝试执行双保险转存（本地硬盘 + OSS）...");
                    String combinedResult = ossService.uploadResultToOss(task.getSpu(), kieResult.getResultUrl(), task.getResolution());

                    String[] parts = combinedResult.split("\\|");
                    if (parts.length == 2) {
                        task.setResultOssUrl(parts[0]);
                        task.setLocalPath("DELETED".equals(parts[1]) ? null : parts[1]);

                        // 🔴 第二段提交：转存成功了，把 OSS 链接补充进数据库
                        imageTaskRepository.save(task);
                        log.info("【转存成功】任务 {} 的 OSS 永久链接补充入库完毕！", task.getId());
                    }
                } catch (Exception subEx) {
                    // 🔴 核心逻辑 4：如果转存报错，仅仅打印日志，绝对不去修改 task 的状态！
                    // 这样前端看到的状态依然是“成功”，并且能通过临时链接看图。
                    log.error("【转存降级】本地保存或 OSS 上传失败，但不影响任务成功状态: {}", subEx.getMessage());
                }

            } else if ("fail".equals(kieResult.getStatus()) || "failed".equals(kieResult.getStatus())) {
                task.setStatus("FAILED");
                task.setErrorMessage(kieResult.getErrorMessage());
                imageTaskRepository.save(task);
            }
        } catch (Throwable e) {
            log.error("刷新任务发生系统崩溃: {}", e.getMessage());
            // 注意：这里捕获的是 KIE 接口查询本身的崩溃。
            // 转存的崩溃已经被上面拦截了，不会走到这里。
            task.setStatus("FAILED");
            task.setErrorMessage("查询远端结果异常: " + e.getMessage());
            imageTaskRepository.save(task);
        }

        return new TaskCreateResponse(task);
    }


    // ======================== 下载与查询逻辑 ========================
    @Override
    public void batchDownloadZip(List<Long> ids, jakarta.servlet.http.HttpServletResponse response) {
        // 设置响应头，告知浏览器这是一个 ZIP 文件下载
        response.setContentType("application/zip");
        // 解决中文乱码问题（如果有中文名），建议使用英文或拼音
        response.setHeader("Content-Disposition", "attachment; filename=AI_tasks_results.zip");

// 直接将 ZIP 流绑定到 HTTP 响应流上，实现“边下载边打包边传给用户”
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {

            boolean hasFiles = false;
            int index = 1; // 🔴 新增：用于兜底的序号

            for (Long id : ids) {
                try {
                    // 因为外面用了流，这里用传统方式获取对象更方便处理异常
                    ImageTask task = imageTaskRepository.findById(id).orElse(null);
                    if (task == null) continue;

                    // 优先取 OSS 永久链接，没有的话取 KIE 的临时链接
                    String imageUrl = task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl();
                    if (imageUrl == null || imageUrl.isEmpty()) continue;

                    String ext = imageUrl.toLowerCase().contains(".jpg") ? ".jpg" : ".png";

                    // 🔴 极简提取：直接使用 OSS 链接末尾的原始文件名（包含颜色、序号、时间戳）
                    String fileName = task.getSpu() + "_" + index + ext; // 默认 SPU 兜底
                    String colorUrl = task.getColorImageUrl();

                    if (colorUrl != null && colorUrl.contains("/")) {
                        try {
                            // 直接截取最后一段，例如得到 "红色_1_1711263456789.jpg"
                            String lastPart = colorUrl.substring(colorUrl.lastIndexOf("/") + 1);
                            fileName = java.net.URLDecoder.decode(lastPart, "UTF-8"); // 解码可能被 URL 编码的中文
                        } catch (Exception e) {
                            log.warn("解析文件名失败", e);
                        }
                    }
                    index++; // 兜底序号自增

                    // 使用 httpClient 直接从网络拉取图片流
                    okhttp3.Request request = new okhttp3.Request.Builder()
                            .url(imageUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                            .build();

                    try (okhttp3.Response okResponse = httpClient.newCall(request).execute()) {
                        if (okResponse.isSuccessful() && okResponse.body() != null) {
                            // 开启一个新的 ZIP 实体
                            zos.putNextEntry(new java.util.zip.ZipEntry(fileName));

                            // 直接将网络的 InputStream 灌入 ZIP 的 OutputStream，完全不占本地硬盘和内存！
                            try (InputStream is = okResponse.body().byteStream()) {
                                byte[] buffer = new byte[8192];
                                int length;
                                while ((length = is.read(buffer)) > 0) {
                                    zos.write(buffer, 0, length);
                                }
                            }
                            zos.closeEntry();
                            hasFiles = true;
                        } else {
                            log.warn("【ZIP打包】单图下载失败: HTTP {}, 链接: {}", okResponse.code(), imageUrl);
                        }
                    }
                } catch (Exception e) {
                    log.error("【ZIP打包】处理任务 ID: {} 时发生异常: {}", id, e.getMessage());
                    // 某一张图失败了，跳过它，继续打包下一张，绝不中断整个下载
                }
            }

            // 如果用户选中的任务全都没图，放一个提示文本进去，防止下载到一个无效的空 ZIP
            if (!hasFiles) {
                zos.putNextEntry(new java.util.zip.ZipEntry("下载失败提示.txt"));
                String msg = "您选中的任务尚未生成结果，或者链接已失效，因此没有任何图片。";
                zos.write(msg.getBytes("UTF-8"));
                zos.closeEntry();
            }

        } catch (Exception e) {
            log.error("【ZIP打包】全局严重异常", e);
            throw new RuntimeException("打包 ZIP 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<TaskCreateResponse> getTaskPage(int page, int size, String spu, String status, Integer taskType, LocalDateTime startTime, LocalDateTime endTime, String taskId) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<ImageTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 🔴 新增：任务 ID 模糊搜索
            if (taskId != null && !taskId.trim().isEmpty()) {
                predicates.add(cb.like(root.get("taskId"), "%" + taskId.trim() + "%"));
            }
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

    @jakarta.annotation.PreDestroy
    public void onDestroy() {
        log.info("【系统关闭】正在释放轮询线程池...");
        if (pollingPool != null) {
            pollingPool.shutdown();
        }
    }

    @Override
    public List<com.ai.dto.SpuStatDTO> getTaskStats(String spu, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 构建查询条件：只查 SUCCESS 的任务，并加上 SPU 和 时间筛选
        Specification<ImageTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "SUCCESS")); // 🔴 核心：失败的不计算

            if (spu != null && !spu.trim().isEmpty()) {
                predicates.add(cb.like(root.get("spu"), "%" + spu.trim() + "%"));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 2. 查出所有符合条件的成功任务
        List<ImageTask> tasks = imageTaskRepository.findAll(spec);

        // 3. 在 Java 内存中按 SPU 分组汇总
        java.util.Map<String, com.ai.dto.SpuStatDTO> statMap = new java.util.HashMap<>();

        for (ImageTask task : tasks) {
            String targetSpu = task.getSpu() != null && !task.getSpu().trim().isEmpty() ? task.getSpu().trim() : "未知款号";

            com.ai.dto.SpuStatDTO dto = statMap.computeIfAbsent(targetSpu, k -> {
                com.ai.dto.SpuStatDTO newDto = new com.ai.dto.SpuStatDTO();
                newDto.setSpu(k);
                return newDto;
            });

            dto.setTaskCount(dto.getTaskCount() + 1);

            // 🔴 核心算法：2K 算 0.22元，4K 算 0.44元
            if ("4K".equalsIgnoreCase(task.getResolution())) {
                dto.setCount4K(dto.getCount4K() + 1);
                dto.setTotalCost(dto.getTotalCost() + 0.44);
            } else {
                dto.setCount2K(dto.getCount2K() + 1);
                dto.setTotalCost(dto.getTotalCost() + 0.22);
            }
        }

        // 4. 按总消费金额降序排列返回
        return statMap.values().stream()
                .sorted((a, b) -> Double.compare(b.getTotalCost(), a.getTotalCost()))
                .collect(Collectors.toList());
    }
}