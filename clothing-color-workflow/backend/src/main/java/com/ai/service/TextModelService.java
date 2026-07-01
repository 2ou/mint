package com.ai.service;

import com.ai.dto.ModelGenerateRequest;

/**
 * 文本模型服务 - 调用 LLM 生成提示词
 */
public interface TextModelService {

    /**
     * 调用文本模型生成提示词
     * @param request 用户需求
     * @param modelType 使用的模型：gpt
     * @return 生成的提示词
     */
    String generatePrompt(ModelGenerateRequest request, String modelType);

    /**
     * 使用调用方提供的完整 system/user prompt 调用文本模型。
     */
    String generateRawPrompt(String systemPrompt, String userPrompt, String modelType);
}
