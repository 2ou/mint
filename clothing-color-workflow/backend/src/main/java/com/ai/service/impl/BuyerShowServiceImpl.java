package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.exception.BusinessException;
import com.ai.service.BuyerShowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 买家秀生成服务实现
 * 融合 buyer-show-generator skill 的差异化规则
 * 支持多图+场景偏好+按图分组输出
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuyerShowServiceImpl implements BuyerShowService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String GPT_API_URL = KieGptModels.RESPONSES_API_URL;

    @Override
    public String generateBuyerShow(String spu, String clothingDesc, List<String> imageUrls,
                                     String scenePreference, int countPerImage, String textModel) {
        if (spu == null || spu.trim().isEmpty()) {
            throw new BusinessException("请填写SPU款号");
        }
        if (clothingDesc == null || clothingDesc.trim().isEmpty()) {
            throw new BusinessException("请提供服装描述");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException("请上传至少一张产品图");
        }
        if (countPerImage < 1) countPerImage = 1;
        if (countPerImage > 5) countPerImage = 5;
        if (textModel == null || textModel.isEmpty()) {
            textModel = "gpt";
        }

        int totalPrompts = imageUrls.size() * countPerImage;
        String systemPrompt = buildSystemPrompt(totalPrompts, imageUrls.size(), countPerImage, scenePreference);
        String userPrompt = buildUserPrompt(clothingDesc, imageUrls, scenePreference, countPerImage);

        try {
            return callGpt(systemPrompt, userPrompt);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成买家秀失败: {}", e.getMessage(), e);
            throw new BusinessException("生成买家秀失败: " + e.getMessage());
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(int totalPrompts, int imageCount, int countPerImage, String scenePreference) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert Amazon fashion e-commerce buyer show (买家秀) prompt engineer.\n\n");
        sb.append("Your task: Generate ").append(totalPrompts).append(" buyer show English prompts for ");
        sb.append(imageCount).append(" product images (").append(countPerImage).append(" prompts per image).\n\n");

        // 核心铁律
        sb.append("## CORE RULES\n\n");
        sb.append("### LOCKED (never change)\n");
        sb.append("- Each image has its OWN clothing color — the prompt MUST match the image's color exactly\n");
        sb.append("- Clothing style, fabric, cut must remain consistent\n\n");

        sb.append("### VARIABLES (must differ for each prompt)\n");
        sb.append("- Model: different body type, ethnicity, age, facial features, signature traits\n");
        sb.append("- Scene: different lifestyle location with specific details\n");
        sb.append("- Accessories: match the scene\n");
        sb.append("- Shoes: match the scene\n");
        sb.append("- Pose/action: natural, scene-appropriate\n\n");

        // 场景偏好
        if (scenePreference != null && !scenePreference.trim().isEmpty()) {
            sb.append("## USER SCENE PREFERENCE (IMPORTANT — prioritize these scenes)\n");
            sb.append("The user wants these specific scenes: ").append(scenePreference).append("\n");
            sb.append("Distribute these scenes across the prompts. If not enough scenes, supplement with matching lifestyle scenes.\n\n");
        }

        // 差异化规则
        sb.append("## DIFFERENTIATION RULES\n\n");

        sb.append("### 1. Model Differentiation\n");
        sb.append("Each model MUST differ in at least 3 dimensions:\n");
        sb.append("- Face shape / Facial features / Signature trait / Hair / Expression / Body language\n\n");

        sb.append("### 2. Scene Differentiation\n");
        sb.append("Each scene MUST have at least 2 specific details (visual + sensory).\n\n");

        sb.append("### 3. Prompt Structure\n");
        sb.append("Vary the opening for each prompt (scene/action/emotion/composition).\n\n");

        sb.append("### 4. Anti-Plastic Keywords\n");
        sb.append("Pick 3-4 from pool per prompt (rotate): visible pores / subtle skin imperfections / candid unguarded moment / no airbrushing / slight film grain / unposed natural gesture\n\n");

        sb.append("### 5. Shooting Style\n");
        sb.append("Rotate: 35mm documentary / 50mm lifestyle / 85mm portrait / iPhone snapshot / 35mm film / Sony A7R crisp\n\n");

        // 美式风格
        sb.append("## AMERICAN STYLE\n");
        sb.append("American lifestyle, natural and authentic, body positive, realistic skin.\n");
        sb.append("Forbidden: Chinese architecture, overly retouched, porcelain white skin, stiff poses.\n\n");

        // 场景配饰速查
        sb.append("## SCENE-ACCESSORY QUICK REFERENCE\n");
        sb.append("| Scene | Accessories | Shoes | Pose |\n");
        sb.append("|-------|-------------|-------|------|\n");
        sb.append("| Living room | bracelet, earrings | barefoot/slippers | sitting on sofa |\n");
        sb.append("| Kitchen | watch, earrings | barefoot/flat | leaning on counter |\n");
        sb.append("| Coffee shop | watch, pendant | flats/loafers | sipping coffee |\n");
        sb.append("| Street | tote, sunglasses | heels/boots | walking |\n");
        sb.append("| Beach | sunglasses, hat | barefoot/flip-flops | at water's edge |\n");
        sb.append("| Park | sunglasses, bag | sneakers/sandals | on bench |\n");
        sb.append("| Office | watch, necklace | heels | at desk |\n\n");

        // 输出格式
        sb.append("## OUTPUT FORMAT (CRITICAL)\n");
        sb.append("Return a single JSON object. Group prompts by image index.\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"groups\": [\n");
        sb.append("    {\n");
        sb.append("      \"imageIndex\": 0,\n");
        sb.append("      \"prompts\": [\n");
        sb.append("        {\n");
        sb.append("          \"index\": 0,\n");
        sb.append("          \"modelDesc\": \"模特描述(中文)\",\n");
        sb.append("          \"sceneDesc\": \"场景描述(中文)\",\n");
        sb.append("          \"prompt\": \"Complete English buyer show prompt matching this image's color\"\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("Rules:\n");
        sb.append("- groups array must have EXACTLY ").append(imageCount).append(" elements\n");
        sb.append("- Each group's prompts array must have EXACTLY ").append(countPerImage).append(" elements\n");
        sb.append("- Total prompts = ").append(totalPrompts).append("\n");
        sb.append("- imageIndex starts from 0, matches the image order\n");
        sb.append("- Each prompt MUST be a COMPLETE English buyer show prompt\n");
        sb.append("- Each prompt MUST match the corresponding image's clothing color\n");
        sb.append("- Do NOT wrap in markdown code blocks — return PURE JSON only\n");

        return sb.toString();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String clothingDesc, List<String> imageUrls,
                                    String scenePreference, int countPerImage) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 服装描述\n").append(clothingDesc).append("\n\n");

        sb.append("## 产品图列表（每张图对应不同颜色）\n");
        for (int i = 0; i < imageUrls.size(); i++) {
            sb.append("图片").append(i).append(": ").append(imageUrls.get(i)).append("\n");
        }
        sb.append("\n");

        if (scenePreference != null && !scenePreference.trim().isEmpty()) {
            sb.append("## 场景偏好\n").append(scenePreference).append("\n\n");
        }

        sb.append("## 要求\n");
        sb.append("共 ").append(imageUrls.size()).append(" 张图，每张图生成 ").append(countPerImage).append(" 条买家秀提示词。\n");
        sb.append("每条提示词的服装颜色必须与对应图片的颜色一致。\n");
        sb.append("每条提示词必须有不同的模特、不同的场景、不同的配饰鞋子姿势。\n\n");
        sb.append("直接返回JSON对象，不要任何其他文字。");

        return sb.toString();
    }

    /**
     * 调用 GPT API
     */
    private String callGpt(String systemPrompt, String userPrompt) throws IOException {
        ObjectMapper om = objectMapper;
        ObjectNode rootNode = om.createObjectNode();
        rootNode.put("model", KieGptModels.GPT_5_5);
        rootNode.put("stream", false);

        ObjectNode reasoning = om.createObjectNode();
        reasoning.put("effort", "medium");
        rootNode.set("reasoning", reasoning);

        ArrayNode input = om.createArrayNode();

        ObjectNode systemMsg = om.createObjectNode();
        systemMsg.put("role", "system");
        ArrayNode systemContent = om.createArrayNode();
        ObjectNode systemText = om.createObjectNode();
        systemText.put("type", "input_text");
        systemText.put("text", systemPrompt);
        systemContent.add(systemText);
        systemMsg.set("content", systemContent);
        input.add(systemMsg);

        ObjectNode userMsg = om.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode userContent = om.createArrayNode();
        ObjectNode userText = om.createObjectNode();
        userText.put("type", "input_text");
        userText.put("text", userPrompt);
        userContent.add(userText);
        userMsg.set("content", userContent);
        input.add(userMsg);

        rootNode.set("input", input);

        String jsonBody = om.writeValueAsString(rootNode);
        log.info("调用 GPT API (买家秀生成)，请求体大小: {} bytes", jsonBody.length());

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(GPT_API_URL)
                .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            log.info("GPT API 响应(买家秀): {}", responseBody.substring(0, Math.min(500, responseBody.length())));

            if (!response.isSuccessful()) {
                throw new RuntimeException("GPT API 调用失败: HTTP " + response.code() + " " + responseBody);
            }

            return GptResponseParser.parseTextOrThrow(om, responseBody, "无法解析 GPT 响应");
        }
    }
}
