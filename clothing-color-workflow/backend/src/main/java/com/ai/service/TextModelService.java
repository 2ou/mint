package com.ai.service;

import com.ai.dto.ModelGenerateRequest;

import java.util.List;

/**
 * 文本模型服务 - 调用 LLM 生成提示词
 */
public interface TextModelService {

    /**
     * 调用文本模型生成提示词
     * @param request 用户需求
     * @param modelType 使用的 GPT 5.6 文本模型
     * @return 生成的提示词
     */
    String generatePrompt(ModelGenerateRequest request, String modelType);

    /**
     * 使用调用方提供的完整 system/user prompt 调用文本模型。
     */
    String generateRawPrompt(String systemPrompt, String userPrompt, String modelType);

    /** Calls the text/vision model with ordered image inputs. */
    String generateRawPromptWithImages(String systemPrompt, String userPrompt, List<String> imageUrls, String modelType);
}
