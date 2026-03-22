package com.ai.service;

import com.ai.dto.KieTaskResult;

public interface KieClientService {

    /**
     * 创建 KIE 任务，返回 taskId
     *
     * @param spu         SPU编号
     * @param prompt      文本提示
     * @param resolution  分辨率
     * @param inputUrl    输入原图OSS URL
     * @param colorUrl    颜色参考图OSS URL
     * @return taskId    KIE 任务ID
     */
    String createTask(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl);

    // 🔴 新增：返回完整结果对象的方法
    KieTaskResult getFullResult(String taskId);

    // 获取 KIE 原始 JSON 报文
    String getRawResult(String taskId);
}