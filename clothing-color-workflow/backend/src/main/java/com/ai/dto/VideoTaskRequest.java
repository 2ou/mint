package com.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class VideoTaskRequest {
    private String spu;
    private String model;
    // 使用 Map 接收前端传来的各种不规则的 input 参数
    private Map<String, Object> input;

    // 🔴 新增：用于前端显式指定存放的图片和视频链接，以及费用
    private String inputImageUrl;  // 参考图片
    private String colorImageUrl;  // 参考视频
    private BigDecimal cost;       // 预估费用
}