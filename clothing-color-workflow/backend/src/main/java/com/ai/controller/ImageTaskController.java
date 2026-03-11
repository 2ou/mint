package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import com.ai.service.ImageTaskService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class ImageTaskController {
    private final ImageTaskService imageTaskService;

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TaskCreateResponse> create(@RequestParam @NotBlank String spu,
                                                  @RequestParam @NotBlank String prompt,
                                                  @RequestParam(defaultValue = "1024") String resolution,
                                                  @RequestParam MultipartFile inputFile,
                                                  @RequestParam MultipartFile colorFile) {
        return ApiResponse.ok("ok", imageTaskService.create(spu, prompt, resolution, inputFile, colorFile));
    }

    @PostMapping("/{id}/refresh")
    public ApiResponse<ImageTask> refresh(@PathVariable Long id) {
        return ApiResponse.ok("ok", imageTaskService.refresh(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<ImageTask> detail(@PathVariable Long id) {
        return ApiResponse.ok("ok", imageTaskService.detail(id));
    }

    @GetMapping("/list")
    public ApiResponse<Page<ImageTask>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ApiResponse.ok("ok", imageTaskService.list(page, size));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        byte[] bytes = imageTaskService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=result_" + id + ".png")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
