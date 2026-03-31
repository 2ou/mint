package com.ai.controller;

import com.ai.dto.BatchTaskRequest;
import com.ai.dto.TaskCreateResponse;
import com.ai.service.ImageTaskService;
import com.ai.dto.ApiResponse;
import com.ai.service.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin // 允许所有跨域请求
@Slf4j
public class ImageTaskController {

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
                        shopName
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


    /**
     * 🔴 新增：获取任务消费统计数据
     */
    @GetMapping("/stats")
    public ApiResponse<List<com.ai.dto.SpuStatDTO>> getTaskStats(
            @RequestParam(value = "spu", required = false) String spu,
            @RequestParam(value = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        List<com.ai.dto.SpuStatDTO> stats = imageTaskService.getTaskStats(spu, startTime, endTime);
        return ApiResponse.ok("ok", stats);
    }

}