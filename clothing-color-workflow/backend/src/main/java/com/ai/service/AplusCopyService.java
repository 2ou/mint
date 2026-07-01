package com.ai.service;

public interface AplusCopyService {
    /**
     * 为指定项目生成 A+ 文案（异步调用文本模型 + 解析 MD）
     */
    void generateCopy(Long projectId);

    void generateCopy(Long projectId, String textModel);
}
