package com.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 批量创建 AI 换色任务的请求对象 (前端直传 OSS 后提交的纯文本 JSON)
 */
@Data
public class BatchTaskRequest {

    /**
     * 商品唯一款号 (如: GC8003)
     */
    private String spu;

    /**
     * 针对 AI 模型生成的提示词 (如: 纯色背景，高清细节...)
     */
    private String prompt;

    /**
     * 生成图片的分辨率，默认 auto 自动适配
     */
    private String resolution = "4k";

    /**
     * 调用的底层 AI 模型，默认使用 nano-banana-pro
     */
    private String model = "nano-banana-pro";

    // 🔴 任务类型，默认为 1 (换色)
    private Integer taskType = 1;

    @JsonProperty("aspect_ratio")
    private String aspectRatio = "auto";

    private String shopName;
    private String operator;

    private java.math.BigDecimal cost;

    /**
     * 前端配对好的任务组合列表 (1张原图对应1张颜色图为一组)
     */
    private List<TaskPair> pairs;

    /**
     * 内部类：单组任务对 (记录已在 OSS 上传成功的图片 URL)
     */
    @Data
    public static class TaskPair {
        /**
         * 原图的 OSS 绝对访问链接
         */
        private String inputUrl;

        /**
         * 颜色参考图的 OSS 绝对访问链接
         */
        private String colorUrl;
    }
}