package com.ai.service;

public interface AplusImageService {
    void generateImages(Long projectId);

    void regenerateModule(Long projectId, String moduleCode);

    int retryFailedModules(Long projectId);
}
