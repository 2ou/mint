package com.ai.service;

import java.util.List;
import java.util.Map;

/**
 * 场景生成服务
 * 基于场景库配置，调用文本模型推荐场景并生成提示词
 */
public interface SceneGeneratorService {

    /**
     * 根据服装描述，AI 推荐最适合的 N 个场景
     * @param clothingDesc 服装描述
     * @param count 推荐数量
     * @param textModel 使用的文本模型: claude / gpt
     * @return 推荐结果 JSON
     */
    String recommendScenes(String clothingDesc, int count, String textModel);

    /**
     * 生成场景提示词（支持场景库ID或自定义场景描述）
     * @param sceneId 场景 ID（与 customScene 二选一）
     * @param customScene 自定义场景描述（与 sceneId 二选一）
     * @param clothingDesc 服装描述
     * @param count 生成提示词数量
     * @param textModel 使用的文本模型: claude / gpt
     * @return 生成的提示词（多条用换行分隔）
     */
    String generatePrompt(String sceneId, String customScene, String clothingDesc, int count, String textModel);

    /**
     * 热更新场景库配置
     */
    void reloadConfig();
}
