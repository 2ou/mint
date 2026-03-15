package com.ai.service;

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

    /**
     * 查询 KIE 任务结果，返回远程图片 URL，如果任务未完成返回 null
     *
     * @param taskId KIE 任务ID
     * @return 生成结果URL，未完成返回 null
     */
    String getResultUrl(String taskId);
}