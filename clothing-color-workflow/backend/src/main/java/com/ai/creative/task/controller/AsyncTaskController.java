package com.ai.creative.task.controller;

import com.ai.creative.task.dto.req.AsyncTaskPageReq;
import com.ai.creative.task.dto.resp.AsyncTaskDetailResp;
import com.ai.creative.task.dto.resp.AsyncTaskLogResp;
import com.ai.creative.task.service.AsyncTaskService;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/creative/tasks")
@RequiredArgsConstructor
public class AsyncTaskController {
    private final AsyncTaskService asyncTaskService;

    @GetMapping("/page")
    public ApiResponse<?> page(AsyncTaskPageReq req){ return ApiResponse.ok("ok", asyncTaskService.page(req)); }
    @GetMapping("/{taskId}")
    public ApiResponse<AsyncTaskDetailResp> detail(@PathVariable Long taskId){ return ApiResponse.ok("ok", asyncTaskService.detail(taskId)); }
    @PostMapping("/{taskId}/refresh")
    public ApiResponse<Void> refresh(@PathVariable Long taskId){ asyncTaskService.refreshTask(taskId); return ApiResponse.ok("ok", null); }
    @PostMapping("/{taskId}/retry")
    public ApiResponse<Void> retry(@PathVariable Long taskId){ asyncTaskService.retryTask(taskId); return ApiResponse.ok("ok", null); }
    @GetMapping("/{taskId}/logs")
    public ApiResponse<List<AsyncTaskLogResp>> logs(@PathVariable Long taskId){ return ApiResponse.ok("ok", asyncTaskService.logs(taskId)); }
}
