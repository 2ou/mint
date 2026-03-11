package com.ai.service;

import com.ai.dto.TaskCreateResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageTaskService {

    /**
     * 创建单个任务（单原图 + 单颜色图）
     */
    TaskCreateResponse create(String spu, String prompt, String resolution,
                              MultipartFile inputFile, MultipartFile colorFile);

    /**
     * 刷新任务状态
     */
    TaskCreateResponse refreshTask(Long id);

    /**
     * 获取单个任务
     */
    TaskCreateResponse getTaskById(Long id);

    /**
     * 获取所有任务列表
     */
    List<TaskCreateResponse> listAllTasks();

    /**
     * 下载任务结果到本地
     */
    void downloadTaskFile(Long id);
}