package com.ai.controller;

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
public class ImageTaskController {

    private final ImageTaskService imageTaskService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<TaskCreateResponse>> create(
            @RequestParam @NotBlank String spu,
            @RequestParam @NotBlank String prompt,
            @RequestParam(defaultValue = "1024") String resolution,
            @RequestParam MultipartFile[] inputFiles,
            @RequestParam MultipartFile[] colorFiles) {

        List<TaskCreateResponse> responses = new ArrayList<>();
        for (MultipartFile inputFile : inputFiles) {
            for (MultipartFile colorFile : colorFiles) {
                TaskCreateResponse response = imageTaskService.create(spu, prompt, resolution, inputFile, colorFile);
                responses.add(response);
            }
        }
        return ApiResponse.ok("ok", responses);
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<TaskCreateResponse> refresh(@PathVariable Long id) {
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
}