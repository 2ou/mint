package com.ai.service;

import com.ai.dto.KieTaskResult;

public interface KieClientService {
    String createTask(String prompt, String resolution, String inputImageUrl, String colorImageUrl);
    KieTaskResult queryTask(String taskId);
}
