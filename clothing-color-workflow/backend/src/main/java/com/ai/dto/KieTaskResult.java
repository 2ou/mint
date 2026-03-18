package com.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KieTaskResult {
    private boolean finished;
    private boolean success;
    private String status;
    private String taskId;
    private String resultUrl;
    private String errorMessage;
    private Long completeTime; // 🔴 对应 KIE 返回的 completeTime
}
