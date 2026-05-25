package com.ai.service;

import com.ai.dto.ModelGenerateRequest;

/**
 * 文本模型服务 - 调用 LLM 生成提示词
 */
public interface TextModelService {

    /**
     * 调用文本模型生成提示词
     * @param request 用户需求
     * @param modelType 使用的模型：claude / gpt
     * @return 生成的提示词
     */
    String generatePrompt(ModelGenerateRequest request, String modelType);
}
