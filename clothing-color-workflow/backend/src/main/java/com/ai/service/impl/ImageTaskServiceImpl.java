package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import com.ai.enums.TaskStatus;
import com.ai.exception.BusinessException;
import com.ai.repository.ImageTaskRepository;
import com.ai.service.ImageTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ImageTaskServiceImpl implements ImageTaskService {
    private final ImageTaskRepository repository;
    private final OssService ossService;
    private final KieClientService kieClientService;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public TaskCreateResponse create(String spu, String prompt, String resolution, MultipartFile inputFile, MultipartFile colorFile) {
        String inputUrl = ossService.uploadInput(spu, "input", inputFile);
        String colorUrl = ossService.uploadInput(spu, "color", colorFile);
        String taskId = kieClientService.createTask(prompt, resolution, inputUrl, colorUrl);

        ImageTask task = new ImageTask();
        task.setSpu(spu);
        task.setPrompt(prompt);
        task.setResolution(resolution);
        task.setModel(appProperties.getKie().getModel());
        task.setTaskId(taskId);
        task.setInputImageUrl(inputUrl);
        task.setColorImageUrl(colorUrl);
        task.setStatus(TaskStatus.PROCESSING);
        task = repository.save(task);

        wait5Seconds();
        KieTaskResult result = kieClientService.queryTask(taskId);
        fillByResult(task, result);

        return TaskCreateResponse.builder()
                .id(task.getId())
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .resultUrl(task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl())
                .message(task.getStatus() == TaskStatus.SUCCESS ? "生成成功" : "任务处理中")
                .build();
    }

    @Override
    @Transactional
    public ImageTask refresh(Long id) {
        ImageTask task = detail(id);
        KieTaskResult result = kieClientService.queryTask(task.getTaskId());
        fillByResult(task, result);
        return task;
    }

    @Override
    public ImageTask detail(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("任务不存在"));
    }

    @Override
    public Page<ImageTask> list(int page, int size) {
        return repository.findAll(PageRequest.of(Math.max(page - 1, 0), size));
    }

    @Override
    public byte[] download(Long id) {
        ImageTask task = detail(id);
        String url = task.getResultOssUrl() != null ? task.getResultOssUrl() : task.getResultTempUrl();
        if (url == null) throw new BusinessException("暂无可下载结果图");
        return ossService.downloadByUrl(url);
    }

    private void wait5Seconds() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fillByResult(ImageTask task, KieTaskResult result) {
        if (result.isSuccess()) {
            task.setResultTempUrl(result.getResultUrl());
            String ossUrl = ossService.transferResultToOss(task.getSpu(), result.getResultUrl());
            task.setResultOssUrl(ossUrl);
            String localPath = saveLocal(task.getSpu(), ossService.downloadByUrl(result.getResultUrl()));
            task.setLocalPath(localPath);
            task.setStatus(TaskStatus.SUCCESS);
        } else if (result.isFinished()) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(result.getErrorMessage() != null ? result.getErrorMessage() : "任务失败");
        } else {
            task.setStatus(TaskStatus.PROCESSING);
        }
        repository.save(task);
    }

    private String saveLocal(String spu, byte[] bytes) {
        try {
            Path dir = Paths.get(appProperties.getLocalSaveRoot(), spu);
            Files.createDirectories(dir);
            Path file = dir.resolve("result_" + System.currentTimeMillis() + ".png");
            Files.write(file, bytes);
            return file.toString();
        } catch (IOException e) {
            throw new BusinessException("保存本地失败: " + e.getMessage());
        }
    }
}
