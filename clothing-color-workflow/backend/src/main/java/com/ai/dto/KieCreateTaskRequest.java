package com.ai.dto;

import lombok.Data;
import java.util.Map;

@Data
public class KieCreateTaskRequest {

    // 模型名称
    private String model;

    // 回调地址（注意这里的 B 是大写，匹配 setCallBackUrl）
    private String callBackUrl;

    // 🔴 使用 Map 接收前端传来的任何动态参数，完美兼容各种模型
    private Map<String, Object> input;

}