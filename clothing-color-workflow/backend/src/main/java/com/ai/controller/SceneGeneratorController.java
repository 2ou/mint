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
        int count = request.containsKey("count") ? Integer.parseInt(request.get("count").toString()) : 3;
        String textModel = (String) request.getOrDefault("textModel", "claude");
        String result = sceneGeneratorService.recommendScenes(clothingDesc, count, textModel);
        return ApiResponse.ok("推荐成功", result);
    }

    /**
     * 生成场景提示词（支持场景库ID或自定义场景）
     */
    @PostMapping("/generate-prompt")
    public ApiResponse<String> generatePrompt(@RequestBody Map<String, Object> request) {
        String sceneId = (String) request.get("sceneId");
        String customScene = (String) request.get("customScene");
        String clothingDesc = (String) request.getOrDefault("clothingDesc", "fashion clothing");
        int count = request.containsKey("count") ? Integer.parseInt(request.get("count").toString()) : 1;
        String textModel = (String) request.getOrDefault("textModel", "claude");

        String prompt = sceneGeneratorService.generatePrompt(sceneId, customScene, clothingDesc, count, textModel);
        return ApiResponse.ok("提示词生成成功", prompt);
    }

    /**
     * 热更新场景库配置
     */
    @PostMapping("/reload-config")
    public ApiResponse<String> reloadConfig() {
        sceneGeneratorService.reloadConfig();
        return ApiResponse.ok("场景库配置已重新加载", null);
    }
}
