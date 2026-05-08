package com.ai.controller;

import com.ai.dto.*;
import com.ai.entity.ImageTask;
import com.ai.entity.SysUser;
import com.ai.enums.TaskStatus;
import com.ai.repository.ImageTaskRepository;
import com.ai.repository.SysUserRepository;
import com.ai.service.ImageTaskService;
import com.ai.service.OssService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin // 允许所有跨域请求
@Slf4j
public class ImageTaskController {

    private final ImageTaskRepository imageTaskRepository;
    private final SysUserRepository sysUserRepository;
    private final ImageTaskService imageTaskService;
    // 🔴 新增：注入 KieClientService
    private final com.ai.service.KieClientService kieClientService;

    /**
     * 批量创建任务 (换色 / 场景生成)
     */
    @PostMapping(value = "/create")
    public ApiResponse<List<TaskCreateResponse>> create(@RequestBody BatchTaskRequest request,
                                                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
        List<TaskCreateResponse> responses = new ArrayList<>();

        // 🔴 1. 确保从拦截器放入 Request 域的属性被正确取出
        String operator = (String) httpServletRequest.getAttribute("operator");
        String shopName = (String) httpServletRequest.getAttribute("shopName");

        log.info("【收到任务请求】操作人: {}, 所属店铺: {}", operator, shopName);

        if (request.getPairs() != null) {
            for (BatchTaskRequest.TaskPair pair : request.getPairs()) {
                TaskCreateResponse response = imageTaskService.createWithUrl(
                        request.getSpu(),
                        request.getPrompt(),
                        request.getResolution(),
                        request.getAspectRatio(),
                        request.getModel(),
                        pair.getInputUrl(),
                        pair.getColorUrl(),
                        request.getTaskType(),
                        operator,
                        shopName,
                        request.getCost()
                );
                responses.add(response);
            }
        }
        return ApiResponse.ok("ok", responses);
    }

    /**
     * 刷新单条任务状态
     * 🔴 修复点：明确指定 PathVariable 的值为 "id"
     */
    @PostMapping("/{id}/refresh")
    public ApiResponse<TaskCreateResponse> refresh(@PathVariable("id") Long id) {
        TaskCreateResponse response = imageTaskService.refreshTask(id);
        return ApiResponse.ok("ok", response);
    }

    /**
     * 大盘列表 (配合前端的极速秒查，直接一次性返回全部数据)
     * 🔴 修复点：移除各种 int 传参，彻底告别 parameter missing 报错
     */
    @GetMapping("/list")
    public ApiResponse<List<TaskCreateResponse>> listTasks() {
        List<TaskCreateResponse> tasks = imageTaskService.listAllTasks();
        return ApiResponse.ok("ok", tasks);
    }

    /**
     * 服务端真分页查询大盘
     */
    @GetMapping("/page")
    public ApiResponse<Page<TaskCreateResponse>> pageTasks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "spu", required = false) String spu,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "taskType", required = false) Integer taskType, // 🔴 新增
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(value = "taskId", required = false) String taskId) {

        Page<TaskCreateResponse> taskPage = imageTaskService.getTaskPage(page, size, spu, status, taskType, startTime, endTime, taskId);
        return ApiResponse.ok("ok", taskPage);
    }

    /**
     * 获取单条任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<TaskCreateResponse> getTask(@PathVariable("id") Long id) {
        TaskCreateResponse response = imageTaskService.getTaskById(id);
        return ApiResponse.ok("ok", response);
    }

    /**
     * 打包下载多个任务结果图
     */
    @PostMapping("/download-zip")
    public void downloadZip(@RequestBody com.ai.dto.BatchIdRequest request, jakarta.servlet.http.HttpServletResponse response) {
        imageTaskService.batchDownloadZip(request.getIds(), response);
    }

    // 在最上方定义一个公用的代理客户端
    private final okhttp3.OkHttpClient proxyClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @GetMapping("/proxy-download")
    public void proxyDownload(@RequestParam("url") String url,
                              @RequestParam("filename") String filename,
                              jakarta.servlet.http.HttpServletResponse response) {
        log.info("【流式透传】准备下载文件: {}", filename);
        log.info("【流式透传】目标 KIE 链接: {}", url);

        try {
            // 🔴 增强防盗链伪装：不仅伪装浏览器，还伪装来源 (Referer)
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    // 如果 KIE 校验来源，随便给个常规域或者它自己的域
                    .addHeader("Referer", "https://aiquickdraw.com/")
                    .build();

            try (okhttp3.Response okResponse = proxyClient.newCall(request).execute()) {

                // 🔴 排错关键：如果 KIE 不给图，把真实的错误码打印到 IDEA 控制台！
                if (!okResponse.isSuccessful() || okResponse.body() == null) {
                    log.error("【流式透传】❌ 失败！KIE厂商拒绝给图。HTTP 状态码: {}", okResponse.code());
                    response.sendError(500, "KIE厂商拒绝给图，状态码: " + okResponse.code());
                    return;
                }

                response.setContentType("application/octet-stream");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8") + "\"");

                String contentLength = okResponse.header("Content-Length");
                if (contentLength != null) {
                    response.setHeader("Content-Length", contentLength);
                }

                log.info("【流式透传】✅ 成功连上 KIE，开始向前端浏览器倒水...");
                try (java.io.InputStream is = okResponse.body().byteStream();
                     java.io.OutputStream os = response.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                    os.flush();
                }
                log.info("【流式透传】🎉 下载完成: {}", filename);
            }
        } catch (Exception e) {
            log.error("【流式透传】❌ 发生 Java 代码层面崩溃: ", e);
            try {
                response.sendError(500, "服务器内部透传错误");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 直接获取 KIE 原生 JSON 报文
     */
    @GetMapping("/kie-raw/{taskId}")
    public ApiResponse<String> getKieRaw(@PathVariable("taskId") String taskId) {
        return ApiResponse.ok("ok", kieClientService.getRawResult(taskId));
    }

    private final OssService ossService;


    @PostMapping("/create-video")
    public ApiResponse<ImageTask> createVideoTask(@RequestBody VideoTaskRequest req, HttpServletRequest request) {

        String token = request.getHeader("X-User-Token");
        if (token == null || token.trim().isEmpty()) {
            return ApiResponse.fail("未登录，缺少请求头 Token");
        }
        SysUser user = sysUserRepository.findByToken(token);
        if (user == null) {
            return ApiResponse.fail("未登录或 Token 已失效");
        }

        ImageTask task = new ImageTask();
        task.setSpu(req.getSpu());
        task.setModel(req.getModel());

        // 🔴 1. 保存预估费用
        task.setCost(req.getCost());

        // 🔴 2. 直接使用前端拆分好的标准链接，无需再到 input 里面去复杂判断捞取
        task.setInputImageUrl(req.getInputImageUrl());
        task.setColorImageUrl(req.getColorImageUrl());

        // 🔴 3. 提取 Prompt (完美兼容 HappyHorse、Seedance、Kling单/多镜头)
        if (req.getInput() != null) {
            Map<String, Object> input = req.getInput();
            if (input.get("prompt") != null) {
                task.setPrompt(input.get("prompt").toString());
            } else if (input.containsKey("multi_prompt")) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> multiPrompts = (List<Map<String, Object>>) input.get("multi_prompt");
                    if (multiPrompts != null && !multiPrompts.isEmpty() && multiPrompts.get(0).containsKey("prompt")) {
                        task.setPrompt(multiPrompts.get(0).get("prompt").toString() + " (+多镜头分镜)");
                    }
                } catch (Exception e) {
                    log.warn("提取多镜头 prompt 失败", e);
                }
            }
        }

        task.setTaskType(3);
        task.setStatus(String.valueOf(TaskStatus.PROCESSING));
        task.setShopName(user.getShopName());
        task.setOperator(user.getUsername());

        imageTaskRepository.save(task);

        try {
            // 底层 API 依然只接收 input Payload，不影响 KIE 执行
            KieTaskResult result = kieClientService.createVideoTask(req.getModel(), req.getInput());
            task.setTaskId(result.getTaskId());
            imageTaskRepository.save(task);
            return ApiResponse.ok("视频生成任务已下发", task);
        } catch (Exception e) {
            task.setStatus(String.valueOf(TaskStatus.FAILED));
            task.setErrorMessage(e.getMessage());
            imageTaskRepository.save(task);
            return ApiResponse.fail("视频任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务（最高权限：仅限 PINKSIR 店铺操作）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTask(@PathVariable("id") Long id, HttpServletRequest request) {
        // 1. 获取当前操作人的店铺名和姓名 (由 TokenInterceptor 注入)
        String shopName = (String) request.getAttribute("shopName");
        String operator = (String) request.getAttribute("operator");

        // 2. 权限拦截：强制校验是否为 PINKSIR
        if (shopName == null || !"PINKSIR".equalsIgnoreCase(shopName.trim())) {
            log.warn("【越权拦截】店铺 [{}] 的员工 [{}] 尝试删除任务 {} 被拒绝！", shopName, operator, id);
            return ApiResponse.fail("无权限操作：仅限 PINKSIR 内部管理删除");
        }

        try {
            // 3. 检查任务是否存在
            if (!imageTaskRepository.existsById(id)) {
                return ApiResponse.fail("删除失败：该任务 ID 不存在");
            }

            // 4. 执行删除
            imageTaskRepository.deleteById(id);
            log.info("【任务删除】✅ 员工 [{}] 成功删除了任务 ID: {}", operator, id);

            return ApiResponse.ok("删除成功", null);
        } catch (Exception e) {
            log.error("【任务删除】❌ 发生异常: ", e);
            return ApiResponse.fail("删除失败：" + e.getMessage());
        }
    }
}