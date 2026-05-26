package com.ai.service;

/**
 * 场景生成服务
 * 场景由 AI 文本模型生成，场景库 skill 辅助提升提示词质量
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
     * 根据场景描述生成场景提示词
     * @param sceneDesc 场景描述（AI推荐场景的description或用户自定义输入）
     * @param clothingDesc 服装描述
     * @param count 生成提示词数量
     * @param textModel 使用的文本模型: claude / gpt
     * @return 生成的提示词 JSON
     */
    String generatePrompt(String sceneDesc, String clothingDesc, int count, String textModel);

    /**
     * 重新加载场景库 skill 辅助知识
     */
    void reloadSkillKnowledge();
}
