package com.ai.service;

import java.util.List;

/**
 * 买家秀生成服务
 * 根据服装描述和多张产品图，AI智能生成不同场景的买家秀提示词。
 */
public interface BuyerShowService {

    /**
     * 生成买家秀提示词
     * @param spu 款号（必填）
     * @param clothingDesc 服装描述
     * @param imageUrls 产品图 URL 列表（多张不同颜色）
     * @param scenePreference 场景偏好（用户自由输入，可为空）
     * @param countPerImage 每张图生成几条提示词（默认1）
     * @param textModel 使用的 GPT 5.6 文本模型
     * @return 买家秀提示词 JSON（按图片分组）
     */
    String generateBuyerShow(String spu, String clothingDesc, List<String> imageUrls,
                             String scenePreference, int countPerImage, String textModel);
}
