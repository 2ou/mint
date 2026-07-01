package com.ai.service;

import com.ai.dto.KieTaskResult;

import java.util.Map;

public interface KieClientService {

    /**
     * 创建 KIE 任务，返回 taskId
     *
     * @param spu         SPU编号
     * @param prompt      文本提示
     * @param resolution  分辨率
     * @param aspectRatio 画面比例
     * @param model       图片模型
     * @param inputUrl    输入原图OSS URL
     * @param colorUrl    颜色参考图OSS URL
     * @param callBackUrl 回调地址，为空则走轮询
     * @return taskId    KIE 任务ID
     */
    String createTask(String spu, String prompt, String resolution, String aspectRatio, String model, String inputUrl, String colorUrl, String callBackUrl);

    // 🔴 新增：返回完整结果对象的方法
    KieTaskResult getFullResult(String taskId);

    // 获取 KIE 原始 JSON 报文
    String getRawResult(String taskId);

    KieTaskResult createVideoTask(String model, Map<String, Object> input);
}