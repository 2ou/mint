package com.ai.dto;

import lombok.Data;
import java.util.Map;

@Data
public class VideoTaskRequest {
    private String spu;
    private String model;
    // 使用 Map 接收前端传来的各种不规则的 input 参数
    private Map<String, Object> input;
}