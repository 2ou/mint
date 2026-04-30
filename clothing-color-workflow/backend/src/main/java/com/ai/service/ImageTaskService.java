package com.ai.service;

import com.ai.dto.TaskCreateResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface ImageTaskService {


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

    /**
     * 基于已有的 OSS 链接直接创建任务 (适用于前端直传架构)
     *
     * @param spu        商品款号
     * @param prompt     提示词
     * @param resolution 分辨率
     * @param model      AI 模型名称
     * @param inputUrl   已上传到 OSS 的原图完整 URL
     * @param colorUrl   已上传到 OSS 的颜色图完整 URL
     * @return 任务创建结果 (包含落库后的任务 ID)
     */
    TaskCreateResponse createWithUrl(String spu, String prompt, String resolution, String aspectRatio, String model, String inputUrl, String colorUrl, Integer taskType, String operator, String shopName);

    void batchDownloadZip(List<Long> ids, jakarta.servlet.http.HttpServletResponse response);

    Page<TaskCreateResponse> getTaskPage(int page, int size, String spu, String status, Integer taskType, LocalDateTime startTime, LocalDateTime endTime, String taskId);
}