package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.service.SceneGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 场景生成控制器
 */
@RestController
@RequestMapping("/api/scene-generator")
@RequiredArgsConstructor
@CrossOrigin
public class SceneGeneratorController {

    private final SceneGeneratorService sceneGeneratorService;

    /**
     * AI 推荐场景
     */
    @PostMapping("/recommend")
    public ApiResponse<String> recommendScenes(@RequestBody Map<String, Object> request) {
        String clothingDesc = (String) request.getOrDefault("clothingDesc", "fashion clothing");
        int count = 3;
        if (request.containsKey("count")) {
            try {
                count = Integer.parseInt(request.get("count").toString());
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        String textModel = (String) request.getOrDefault("textModel", "gpt");
        String clothingImageUrl = (String) request.getOrDefault("clothingImageUrl", "");
        String result = sceneGeneratorService.recommendScenes(clothingDesc, count, textModel, clothingImageUrl);
        return ApiResponse.ok("推荐成功", result);
    }

    /**
     * 生成场景提示词（根据场景描述，AI 生成）
     */
    @PostMapping("/generate-prompt")
    public ApiResponse<String> generatePrompt(@RequestBody Map<String, Object> request) {
        String sceneDesc = (String) request.get("sceneDesc");
        if (sceneDesc == null || sceneDesc.trim().isEmpty()) {
            return ApiResponse.fail("请提供场景描述");
        }
        String clothingDesc = (String) request.getOrDefault("clothingDesc", "fashion clothing");
        int count = 1;
        if (request.containsKey("count")) {
            try {
                count = Integer.parseInt(request.get("count").toString());
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        String textModel = (String) request.getOrDefault("textModel", "gpt");
        String clothingImageUrl = (String) request.getOrDefault("clothingImageUrl", "");

        String prompt = sceneGeneratorService.generatePrompt(sceneDesc, clothingDesc, count, textModel, clothingImageUrl);
        return ApiResponse.ok("提示词生成成功", prompt);
    }

    /**
     * 重新加载场景库 skill 辅助知识
     */
    @PostMapping("/reload-skill")
    public ApiResponse<String> reloadSkill() {
        sceneGeneratorService.reloadSkillKnowledge();
        return ApiResponse.ok("场景库 skill 重新加载成功", null);
    }
}
