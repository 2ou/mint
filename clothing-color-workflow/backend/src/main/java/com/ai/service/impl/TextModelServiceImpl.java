package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.ModelGenerateRequest;
import com.ai.exception.BusinessException;
import com.ai.service.TextModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文本模型服务实现
 * 调用 KIE 平台的 Claude / GPT 文本模型生成提示词
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextModelServiceImpl implements TextModelService {

    private final AppProperties appProperties;
    private final ModelPromptGenerator modelPromptGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    // Claude API 端点
    private static final String CLAUDE_API_URL = "https://api.kie.ai/claude/v1/messages";
    // GPT API 端点
    private static final String GPT_API_URL = "https://api.kie.ai/codex/v1/responses";

    @Override
    public String generatePrompt(ModelGenerateRequest request, String modelType) {
        if (modelType == null || modelType.isEmpty()) {
            modelType = "claude"; // 默认使用 Claude
        }

        // 1. 构建系统提示词（基于 Skill 模板）
        String systemPrompt = buildSystemPrompt(request);

        // 2. 构建用户提示词
        String userPrompt = buildUserPrompt(request);

        // 3. 调用对应的文本模型
        try {
            if ("claude".equalsIgnoreCase(modelType)) {
                return callClaude(systemPrompt, userPrompt);
            } else if ("gpt".equalsIgnoreCase(modelType)) {
                return callGpt(systemPrompt, userPrompt);
            } else {
                throw new BusinessException("不支持的模型类型: " + modelType);
            }
        } catch (Exception e) {
            log.error("调用文本模型失败: {}", e.getMessage(), e);
            throw new BusinessException("生成提示词失败: " + e.getMessage());
        }
    }

    /**
     * 构建系统提示词 - 基于 Skill 模板
     */
    private String buildSystemPrompt(ModelGenerateRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert AI fashion photographer prompt engineer. ");
        sb.append("Your task is to generate professional, detailed prompts for AI image generation models ");
        sb.append("to create realistic fashion model photos for cross-border e-commerce.\n\n");

        sb.append("## Core Principles\n");
        sb.append("- Generate prompts in English\n");
        sb.append("- Focus on realism, avoid plastic/CG look\n");
        sb.append("- Include specific physical details, not generic descriptions\n");
        sb.append("- Add slight imperfections for authenticity (freckles, fine lines, asymmetry)\n");
        sb.append("- Include camera/lens parameters for photographic realism\n");
        sb.append("- Always include anti-plastic constraints at the end\n\n");

        // 注入模特类型模板
        sb.append("## Available Model Types\n");
        Map<String, String> types = modelPromptGenerator.getModelTypes();
        for (Map.Entry<String, String> entry : types.entrySet()) {
            String typeName = entry.getKey();
            var template = modelPromptGenerator.getModelTypeDetail(typeName);
            if (template != null) {
                sb.append("### ").append(typeName).append(" (").append(entry.getValue()).append(")\n");
                sb.append("- Default age: ").append(template.defaultAgeRange).append("\n");
                sb.append("- Body: ").append(template.bodyDescription).append("\n");
                sb.append("- Face: ").append(template.faceDescription).append("\n");
                sb.append("- Hair: ").append(template.hairDescription).append("\n");
                sb.append("- Expression: ").append(template.expressionDescription).append("\n");
                sb.append("- Suitable clothing: ").append(template.suitableClothing).append("\n");
                sb.append("- Not suitable: ").append(template.notSuitableClothing).append("\n");
                if (template.lensRecommendation != null) {
                    sb.append("- Lens: ").append(template.lensRecommendation.focalLength)
                      .append(", ").append(template.lensRecommendation.aperture)
                      .append(", ").append(template.lensRecommendation.lensType)
                      .append(", ").append(template.lensRecommendation.camera).append("\n");
                }
                sb.append("\n");
            }
        }

        // 注入面料词库
        sb.append("## Fabric Keywords\n");
        Map<String, String> fabrics = modelPromptGenerator.getAllFabricTypes();
        for (Map.Entry<String, String> entry : fabrics.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");

        // 输出格式要求
        sb.append("## Output Requirements\n");
        sb.append("Generate a single, complete prompt that can be directly used with AI image generation models.\n");
        sb.append("The prompt should follow this structure:\n");
        sb.append("[Photography style] + [Model description with ethnicity/age/body/face/hair/expression] + ");
        sb.append("[Pose and scene] + [Camera/lens parameters] + [Lighting] + [Anti-plastic constraints]\n\n");
        sb.append("Do NOT include any explanation, just output the prompt text directly.\n");

        return sb.toString();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(ModelGenerateRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("Generate a fashion model prompt with the following requirements:\n\n");

        // 基础参数
        sb.append("- Model type: ").append(request.getModelType() != null ? request.getModelType() : "Commercial").append("\n");

        if (request.getEthnicity() != null && !request.getEthnicity().isEmpty()) {
            sb.append("- Ethnicity: ").append(request.getEthnicity()).append("\n");
        }

        if (request.getAgeRange() != null && !request.getAgeRange().isEmpty()) {
            sb.append("- Age range: ").append(request.getAgeRange()).append("\n");
        }

        // 新增参数 - 发型
        if (request.getHairstyle() != null && !request.getHairstyle().isEmpty()) {
            String hairstyleDesc = getHairstyleDescription(request.getHairstyle());
            sb.append("- Hairstyle: ").append(hairstyleDesc).append("\n");
        }

        // 新增参数 - 肤色
        if (request.getSkinTone() != null && !request.getSkinTone().isEmpty()) {
            String skinToneDesc = getSkinToneDescription(request.getSkinTone());
            sb.append("- Skin tone: ").append(skinToneDesc).append("\n");
        }

        // 新增参数 - 拍摄角度
        if (request.getCameraAngle() != null && !request.getCameraAngle().isEmpty()) {
            String angleDesc = getCameraAngleDescription(request.getCameraAngle());
            sb.append("- Camera angle: ").append(angleDesc).append("\n");
        }

        // 新增参数 - 背景
        if (request.getBackground() != null && !request.getBackground().isEmpty()) {
            String bgDesc = getBackgroundDescription(request.getBackground());
            sb.append("- Background: ").append(bgDesc).append("\n");
        }

        // 新增参数 - 服装描述
        if (request.getClothingDescription() != null && !request.getClothingDescription().isEmpty()) {
            sb.append("- Clothing: ").append(request.getClothingDescription()).append("\n");
        }

        // 服装图 URL（参考图）
        if (request.getClothingImageUrl() != null && !request.getClothingImageUrl().isEmpty()) {
            sb.append("- Clothing image URL (reference): ").append(request.getClothingImageUrl()).append("\n");
            sb.append("Note: If an image-to-image model is used, this image should be used as reference input.\n");
        }

        // 特殊要求
        if (request.getSpecialRequirements() != null && !request.getSpecialRequirements().isEmpty()) {
            sb.append("- Special requirements: ").append(request.getSpecialRequirements()).append("\n");
        }

        sb.append("\nGenerate the complete prompt now. Output ONLY the prompt text, no explanations.");

        return sb.toString();
    }

    /**
     * 发型描述映射
     */
    private String getHairstyleDescription(String hairstyle) {
        switch (hairstyle) {
            case "long_wavy": return "long wavy hair with natural texture and soft waves";
            case "long_straight": return "long straight hair with sleek finish";
            case "short_bob": return "short bob haircut, clean and modern";
            case "pixie": return "pixie cut, short and stylish";
            case "ponytail": return "high ponytail, clean and professional";
            case "low_ponytail": return "low ponytail, relaxed and elegant";
            case "braids": return "braided hairstyle, either single braid or multiple braids";
            case "bun": return "hair in a bun, either messy or neat";
            case "slicked_back": return "slicked back wet look, editorial style";
            case "curly": return "curly hair with natural bounce and volume";
            case "afro": return "natural afro hairstyle, voluminous";
            case "updo": return "elegant updo hairstyle for formal look";
            default: return hairstyle;
        }
    }

    /**
     * 肤色描述映射
     */
    private String getSkinToneDescription(String skinTone) {
        switch (skinTone) {
            case "fair": return "fair/pale skin tone, light complexion";
            case "light": return "light skin tone, natural and bright";
            case "medium": return "medium skin tone, warm and healthy";
            case "olive": return "olive skin tone, Mediterranean/Asian influence";
            case "tan": return "tanned/sun-kissed skin, active lifestyle look";
            case "brown": return "brown skin tone, rich and warm";
            case "dark": return "dark skin tone, deep and beautiful";
            default: return skinTone;
        }
    }

    /**
     * 拍摄角度描述映射
     */
    private String getCameraAngleDescription(String angle) {
        switch (angle) {
            case "composite_panel":
                return "Multi-panel fashion product composite in a horizontal grid layout, 16:9 wide aspect ratio. " +
                       "LEFT PANEL (occupying 1/2 of total width): Waist-up portrait / bust shot showing facial details, neckline, shoulder fit. " +
                       "RIGHT PANELS (occupying 1/2 of total width, divided into 3 equal columns): " +
                       "Full-length front view, full-length side profile view, full-length back view of the SAME model. " +
                       "All panels must show the IDENTICAL person.";
            case "front": return "front-facing view, looking directly at camera";
            case "side": return "side profile view, 90-degree angle";
            case "three_quarter": return "three-quarter angle, slightly turned";
            case "back": return "back view, showing back design";
            case "low_angle": return "low angle shot, looking up, powerful perspective";
            case "high_angle": return "high angle shot, looking down, gentle perspective";
            case "close_up": return "close-up portrait, focus on face details";
            case "full_body": return "full body shot, head to toe";
            default: return angle;
        }
    }

    /**
     * 背景描述映射
     */
    private String getBackgroundDescription(String background) {
        switch (background) {
            case "white_studio": return "clean white studio background, professional lighting";
            case "gray_studio": return "gray gradient studio background, neutral tone";
            case "black_studio": return "black studio background, dramatic lighting";
            case "outdoor_nature": return "outdoor natural setting, greenery and natural light";
            case "urban_street": return "urban street background, city vibes";
            case "beach": return "beach/ocean background, summer feel";
            case "cafe": return "cafe/restaurant interior, lifestyle shot";
            case "home": return "home interior, cozy and warm";
            case "abstract": return "abstract/minimalist background, artistic";
            default: return background;
        }
    }

    /**
     * 调用 Claude API
     */
    private String callClaude(String systemPrompt, String userPrompt) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", "claude-opus-4-7");
        rootNode.put("stream", false);
        rootNode.put("max_tokens", 2000);

        // messages
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        rootNode.set("messages", messages);

        // 将 system prompt 作为 developer 消息
        // Claude API 支持 system 参数
        rootNode.put("system", systemPrompt);

        String jsonBody = objectMapper.writeValueAsString(rootNode);
        log.info("调用 Claude API，请求体大小: {} bytes", jsonBody.length());

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(CLAUDE_API_URL)
                .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("Claude API 响应: {}", responseBody.substring(0, Math.min(500, responseBody.length())));

            if (!response.isSuccessful()) {
                throw new RuntimeException("Claude API 调用失败: HTTP " + response.code() + " " + responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);

            // 解析 Claude 响应格式
            if (root.has("content") && root.get("content").isArray()) {
                JsonNode content = root.get("content");
                for (JsonNode block : content) {
                    if ("text".equals(block.has("type") ? block.get("type").asText() : "")) {
                        return block.get("text").asText().trim();
                    }
                }
            }

            // 兜底：尝试直接获取 text 字段
            if (root.has("text")) {
                return root.get("text").asText().trim();
            }

            throw new RuntimeException("无法解析 Claude 响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));
        }
    }

    /**
     * 调用 GPT API
     */
    private String callGpt(String systemPrompt, String userPrompt) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", "gpt-5-5");
        rootNode.put("stream", false);

        // reasoning
        ObjectNode reasoning = objectMapper.createObjectNode();
        reasoning.put("effort", "medium");
        rootNode.set("reasoning", reasoning);

        // input - 数组格式
        ArrayNode input = objectMapper.createArrayNode();

        // system message
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        ArrayNode systemContent = objectMapper.createArrayNode();
        ObjectNode systemText = objectMapper.createObjectNode();
        systemText.put("type", "input_text");
        systemText.put("text", systemPrompt);
        systemContent.add(systemText);
        systemMsg.set("content", systemContent);
        input.add(systemMsg);

        // user message
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode userContent = objectMapper.createArrayNode();
        ObjectNode userText = objectMapper.createObjectNode();
        userText.put("type", "input_text");
        userText.put("text", userPrompt);
        userContent.add(userText);
        userMsg.set("content", userContent);
        input.add(userMsg);

        rootNode.set("input", input);

        String jsonBody = objectMapper.writeValueAsString(rootNode);
        log.info("调用 GPT API，请求体大小: {} bytes", jsonBody.length());

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(GPT_API_URL)
                .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("GPT API 响应: {}", responseBody.substring(0, Math.min(500, responseBody.length())));

            if (!response.isSuccessful()) {
                throw new RuntimeException("GPT API 调用失败: HTTP " + response.code() + " " + responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);

            // 解析 GPT 响应格式
            if (root.has("output") && root.get("output").isArray()) {
                JsonNode output = root.get("output");
                for (JsonNode item : output) {
                    if ("message".equals(item.has("type") ? item.get("type").asText() : "")) {
                        JsonNode content = item.get("content");
                        if (content != null && content.isArray()) {
                            for (JsonNode block : content) {
                                if ("output_text".equals(block.has("type") ? block.get("type").asText() : "")) {
                                    return block.get("text").asText().trim();
                                }
                            }
                        }
                    }
                }
            }

            // 兜底
            if (root.has("text")) {
                return root.get("text").asText().trim();
            }

            throw new RuntimeException("无法解析 GPT 响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));
        }
    }
}
