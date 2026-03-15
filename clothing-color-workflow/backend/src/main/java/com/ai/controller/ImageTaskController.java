package com.ai.controller;

import com.ai.dto.BatchTaskRequest;
import com.ai.dto.TaskCreateResponse;
import com.ai.service.ImageTaskService;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin // 加这行，允许所有跨域请求
public class ImageTaskController {

    private final ImageTaskService imageTaskService;

    /**
     * 批量创建换色任务 (纯 JSON 交互，不接收物理文件)
     * * @param request 包含 SPU、模型参数以及多组【原图URL+颜色图URL】的纯文本请求体
     * @return 返回生成的任务列表详细信息
     */
    @PostMapping(value = "/create")
    public ApiResponse<List<TaskCreateResponse>> create(@RequestBody BatchTaskRequest request) {
        List<TaskCreateResponse> responses = new ArrayList<>();

        // 防空判断：确保前端传来了配对数据
        if (request.getPairs() != null) {
            // 遍历前端传来的 URL 组合，拆解为一个个独立任务去底层排队
            for (BatchTaskRequest.TaskPair pair : request.getPairs()) {
                TaskCreateResponse response = imageTaskService.createWithUrl(
                        request.getSpu(),
                        request.getPrompt(),
                        request.getResolution(),
                        request.getModel(),
                        pair.getInputUrl(),
                        pair.getColorUrl()
                );
                responses.add(response);
            }
        }

        // 返回全部任务的创建结果，供前端展示进度
        return ApiResponse.ok("ok", responses);
    }


    @PostMapping("/{id}/refresh")
    public ApiResponse<TaskCreateResponse> refresh(@PathVariable("id") Long id) {
        TaskCreateResponse response = imageTaskService.refreshTask(id);
        return ApiResponse.ok("ok", response);
    }

    @GetMapping("/list")
    public ApiResponse<List<TaskCreateResponse>> listAll() {
        List<TaskCreateResponse> tasks = imageTaskService.listAllTasks();
        return ApiResponse.ok("ok", tasks);
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskCreateResponse> getTask(@PathVariable Long id) {
        TaskCreateResponse response = imageTaskService.getTaskById(id);
        return ApiResponse.ok("ok", response);
    }

    @GetMapping("/{id}/download")
    public void downloadTask(@PathVariable Long id) {
        imageTaskService.downloadTaskFile(id);
    }

    @PostMapping("/download-zip")
    public void downloadZip(@RequestBody com.ai.dto.BatchIdRequest request, jakarta.servlet.http.HttpServletResponse response) {
        imageTaskService.batchDownloadZip(request.getIds(), response);
    }
}