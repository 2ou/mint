package com.ai.controller;

import com.ai.dto.BatchTaskRequest;
import com.ai.dto.TaskCreateResponse;
import com.ai.service.ImageTaskService;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin // 允许所有跨域请求
public class ImageTaskController {

    private final ImageTaskService imageTaskService;

    /**
     * 批量创建任务 (换色 / 场景生成)
     */
    @PostMapping(value = "/create")
    public ApiResponse<List<TaskCreateResponse>> create(@RequestBody BatchTaskRequest request,
                                                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
        List<TaskCreateResponse> responses = new ArrayList<>();

        // 🔴 从拦截器里拿出当前登录人的身份信息
        String operator = (String) httpServletRequest.getAttribute("operator");
        String shopName = (String) httpServletRequest.getAttribute("shopName");

        if (request.getPairs() != null) {
            for (BatchTaskRequest.TaskPair pair : request.getPairs()) {
                TaskCreateResponse response = imageTaskService.createWithUrl(
                        request.getSpu(),
                        request.getPrompt(),
                        request.getResolution(),
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
            @RequestParam(value = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        Page<TaskCreateResponse> taskPage = imageTaskService.getTaskPage(page, size, spu, status, taskType, startTime, endTime);
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
}