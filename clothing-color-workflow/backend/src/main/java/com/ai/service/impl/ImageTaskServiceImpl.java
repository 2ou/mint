package com.ai.service.impl;

import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import com.ai.repository.ImageTaskRepository;
import com.ai.service.ImageTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageTaskServiceImpl implements ImageTaskService {

    private final ImageTaskRepository imageTaskRepository;
    private final KieClientService kieClientService;
    private final OssService ossService;

    @Value("${app.wait-result-ms:5000}")
    private long waitResultMs;

    @Value("${app.local-save-root}")
    private String localSaveRoot;

    @Override
    public TaskCreateResponse refreshTask(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (!"SUCCESS".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            try {
                String resultUrl = kieClientService.getResultUrl(task.getTaskId());
                if (resultUrl != null) {
                    task.setResultTempUrl(resultUrl);
                    String resultOssUrl = ossService.uploadResultToOss(task.getSpu(), resultUrl);
                    task.setResultOssUrl(resultOssUrl);
                    String localPath = ossService.saveResultToLocal(task.getSpu(), resultUrl, localSaveRoot);
                    task.setLocalPath(localPath);
                    task.setStatus("SUCCESS");
                } else {
                    task.setStatus("PROCESSING");
                }
                imageTaskRepository.save(task);
            } catch (Exception e) {
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
                imageTaskRepository.save(task);
            }
        }

        return new TaskCreateResponse(task);
    }

    @Override
    public TaskCreateResponse getTaskById(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return new TaskCreateResponse(task);
    }

    @Override
    public List<TaskCreateResponse> listAllTasks() {
        List<TaskCreateResponse> list = new ArrayList<>();
        for (ImageTask task : imageTaskRepository.findAll()) {
            list.add(new TaskCreateResponse(task));
        }
        return list;
    }

    @Override
    public void downloadTaskFile(Long id) {
        ImageTask task = imageTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        ossService.saveResultToLocal(task.getSpu(), task.getResultTempUrl(), localSaveRoot);
    }

    @Override
    public TaskCreateResponse createWithUrl(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl) {
        // 1. 初始化并持久化任务记录到 MySQL
        ImageTask task = new ImageTask();
        task.setSpu(spu);
        task.setPrompt(prompt);
        task.setResolution(resolution);
        task.setModel(model); // 记录前端指定使用的 AI 模型
        task.setInputImageUrl(inputUrl);
        task.setColorImageUrl(colorUrl);
        task.setStatus("CREATED");
        imageTaskRepository.save(task);

        try {
            // 2. 调用远端 AI 接口投递任务
            String taskId = kieClientService.createTask(spu, prompt, resolution, model, inputUrl, colorUrl);

            // 3. 投递成功，更新远端 taskId 和状态 (不阻塞等待出图)
            task.setTaskId(taskId);
            task.setStatus("PROCESSING");
        } catch (Exception e) {
            // 投递异常时，记录失败状态和错误信息
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
        }

        // 4. 更新数据库状态并返回
        imageTaskRepository.save(task);
        return new TaskCreateResponse(task);
    }
}