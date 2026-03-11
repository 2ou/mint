package com.ai.dto;

import com.ai.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskCreateResponse {
    private Long id;
    private String taskId;
    private TaskStatus status;
    private String message;
    private String resultUrl;
}
