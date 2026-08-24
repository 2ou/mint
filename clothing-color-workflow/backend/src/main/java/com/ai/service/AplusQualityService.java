package com.ai.service;

/** Performs a multimodal release check after an A+ image task succeeds. */
public interface AplusQualityService {
    void evaluate(Long taskId);
}
