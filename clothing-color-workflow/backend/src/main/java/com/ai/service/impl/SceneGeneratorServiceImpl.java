package com.ai.service.impl;

import com.ai.exception.BusinessException;
import com.ai.service.SceneGeneratorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.ai.config.AppProperties;
import lombok.RequiredArgsConstructor;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 场景生成服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneGeneratorServiceImpl implements SceneGeneratorService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, Object> config = new LinkedHashMap<>();
    private List<Map<String, Object>> categories = new ArrayList<>();
    private Map<String, Map<String, Object>> sceneMap = new LinkedHashMap<>();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String CLAUDE_API_URL = "https://api.kie.ai/claude/v1/messages";
    private static final String GPT_API_URL = "https://api.kie.ai/codex/v1/responses";

    @PostConstruct
    public void init() {
        reloadConfig();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void reloadConfig() {
        try {
            InputStream is = null;

            // 1. 尝试从文件系统加载
            String configPath = System.getProperty("app.config.path", "docs/skills/scene-library.json");
            Path filePath = Paths.get(configPath);
            if (Files.exists(filePath)) {
                is = Files.newInputStream(filePath);
                log.info("从文件系统加载场景库配置: {}", filePath.toAbsolutePath());
            }

            // 2. 尝试从 classpath 加载
            if (is == null) {
                ClassPathResource resource = new ClassPathResource("skills/scene-library.json");
                if (resource.exists()) {
                    is = resource.getInputStream();
                    log.info("从 classpath 加载场景库配置");
                }
            }

            if (is != null) {
                config = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                is.close();
            } else {
                log.warn("未找到场景库配置文件，使用空配置");
                config = new LinkedHashMap<>();
                return;
            }

            // 解析分类和场景
            categories = new ArrayList<>();
            sceneMap = new LinkedHashMap<>();

            List<Map<String, Object>> cats = (List<Map<String, Object>>) config.get("categories");
            if (cats != null) {
                for (Map<String, Object> cat : cats) {
                    Map<String, Object> catInfo = new LinkedHashMap<>();
                    catInfo.put("id", cat.get("id"));
                    catInfo.put("name", cat.get("name"));
                    catInfo.put("icon", cat.get("icon"));
                    catInfo.put("description", cat.get("description"));
                    categories.add(catInfo);

                    List<Map<String, Object>> scenes = (List<Map<String, Object>>) cat.get("scenes");
                    if (scenes != null) {
                        for (Map<String, Object> scene : scenes) {
                            scene.put("category", cat.get("id"));
                            sceneMap.put((String) scene.get("id"), scene);
                        }
                    }
                }
            }

            log.info("场景库配置加载成功：{} 个分类，{} 个场景", categories.size(), sceneMap.size());

        } catch (Exception e) {
            log.error("加载场景库配置失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String recommendScenes(String clothingDesc, int count, String textModel) {
        if (clothingDesc == null || clothingDesc.trim().isEmpty()) {
            clothingDesc = "fashion clothing";
        }
        if (count < 1) count = 3;
        if (count > 5) count = 5;
        if (textModel == null || textModel.isEmpty()) {
            textModel = "claude";
        }

        String systemPrompt = buildRecommendSystemPrompt();
        String userPrompt = "服装描述：" + clothingDesc + "\n\n请从场景库中推荐 " + count + " 个最适合的场景，并为每个场景生成1条完整的英文场景图提示词（放在prompts数组中）。\n\n直接返回JSON对象，不要任何其他文字。";

        try {
            if ("claude".equalsIgnoreCase(textModel)) {
                return callClaude(systemPrompt, userPrompt);
            } else if ("gpt".equalsIgnoreCase(textModel)) {
                return callGpt(systemPrompt, userPrompt);
            } else {
                throw new BusinessException("不支持的模型类型: " + textModel);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("推荐场景失败: {}", e.getMessage(), e);
            throw new BusinessException("推荐场景失败: " + e.getMessage());
        }
    }

    @Override
    public String generatePrompt(String sceneId, String customScene, String clothingDesc, int count, String textModel) {
        if (count < 1) count = 1;
        if (count > 10) count = 10;
        if (clothingDesc == null || clothingDesc.trim().isEmpty()) {
            clothingDesc = "fashion clothing";
        }
        if (textModel == null || textModel.isEmpty()) {
            textModel = "claude";
        }

        String systemPrompt = buildGenerateSystemPrompt();

        StringBuilder userPb = new StringBuilder();

        // 判断是场景库场景还是自定义场景
        if (customScene != null && !customScene.trim().isEmpty()) {
            userPb.append("## 场景描述\n").append(customScene).append("\n\n");
        } else if (sceneId != null && !sceneId.isEmpty()) {
            Map<String, Object> scene = sceneMap.get(sceneId);
            if (scene == null) {
                throw new BusinessException("未找到场景: " + sceneId);
            }
            userPb.append("## 选中的场景\n");
            userPb.append("- 场景名称：").append(scene.get("name")).append("\n");
            userPb.append("- 场景描述：").append(scene.get("atmosphere")).append("\n");
            userPb.append("- 光线：").append(scene.get("lighting")).append("\n");
            userPb.append("- 道具：").append(scene.get("props")).append("\n");
            userPb.append("- 提示词模板：").append(scene.get("promptTemplate")).append("\n\n");
        } else {
            throw new BusinessException("请提供场景ID或自定义场景描述");
        }

        userPb.append("## 服装描述\n").append(clothingDesc).append("\n\n");

        if (count > 1) {
            userPb.append("## 要求\n");
            userPb.append("请生成 ").append(count).append(" 条不同的提示词，每条从不同角度/构图/光线描述同一场景，放在prompts数组中。\n\n");
        }

        userPb.append("直接返回JSON对象，不要任何其他文字。");

        try {
            String result;
            if ("claude".equalsIgnoreCase(textModel)) {
                result = callClaude(systemPrompt, userPb.toString());
            } else if ("gpt".equalsIgnoreCase(textModel)) {
                result = callGpt(systemPrompt, userPb.toString());
            } else {
                throw new BusinessException("不支持的模型类型: " + textModel);
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成提示词失败: {}", e.getMessage(), e);
            throw new BusinessException("生成提示词失败: " + e.getMessage());
        }
    }

    // ========== 私有方法 ==========

    /**
     * 构建推荐场景的系统提示词
     */
    @SuppressWarnings("unchecked")
    private String buildRecommendSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert fashion e-commerce scene advisor for Amazon US market.\n\n");
        sb.append("Your task: Based on the clothing description, recommend the 3-5 BEST scenes from the scene library below.\n\n");

        // 注入风格指南
        Map<String, Object> styleGuide = (Map<String, Object>) config.get("styleGuide");
        if (styleGuide != null) {
            sb.append("## Style Guide\n");
            sb.append("- Core style: ").append(styleGuide.get("coreStyle")).append("\n");
            List<String> mustEmphasize = (List<String>) styleGuide.get("mustEmphasize");
            if (mustEmphasize != null) {
                sb.append("- Must emphasize: ").append(String.join(", ", mustEmphasize)).append("\n");
            }
            List<String> forbidden = (List<String>) styleGuide.get("forbiddenElements");
            if (forbidden != null) {
                sb.append("- Forbidden: ").append(String.join(", ", forbidden)).append("\n");
            }
            sb.append("\n");
        }

        // 注入完整场景库
        sb.append("## Scene Library\n\n");
        for (Map<String, Object> cat : categories) {
            sb.append("### ").append(cat.get("icon")).append(" ").append(cat.get("name")).append("\n");
            String catId = (String) cat.get("id");
            for (Map<String, Object> scene : sceneMap.values()) {
                if (catId.equals(scene.get("category"))) {
                    sb.append("- **").append(scene.get("name")).append("** (ID: ").append(scene.get("id")).append(")\n");
                    sb.append("  氛围: ").append(scene.get("atmosphere")).append("\n");
                    sb.append("  光线: ").append(scene.get("lighting")).append("\n");
                    sb.append("  道具: ").append(scene.get("props")).append("\n");
                    sb.append("  适用服装: ").append(scene.get("suitableClothing")).append("\n\n");
                }
            }
        }

        // 输出格式要求
        sb.append("## Output Format (CRITICAL: return EXACTLY this JSON structure)\n");
        sb.append("Return a single JSON object with this structure:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"mode\": \"recommend\",\n");
        sb.append("  \"scenes\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"Modern Office\",\n");
        sb.append("      \"name_cn\": \"现代办公室\",\n");
        sb.append("      \"description\": \"Modern open-plan office with natural light, professional atmosphere\",\n");
        sb.append("      \"match_score\": 95,\n");
        sb.append("      \"reason_cn\": \"职业装与办公室场景高度匹配，自然光线能呈现面料质感\",\n");
        sb.append("      \"prompts\": [\n");
        sb.append("        \"Modern open-plan office with floor-to-ceiling windows and natural light, a confident woman...\"\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("Rules:\n");
        sb.append("- Return exactly ONE JSON object, NOT an array\n");
        sb.append("- scenes array must contain exactly the requested number of scenes\n");
        sb.append("- Each scene MUST have a prompts array with exactly 1 element (the full English prompt)\n");
        sb.append("- prompts must be complete English scene image generation prompts, following all style requirements\n");
        sb.append("- Do NOT wrap in markdown code blocks — return PURE JSON only\n");
        sb.append("- Sort by match_score descending\n");

        return sb.toString();
    }

    /**
     * 构建生成提示词的系统提示词
     */
    private String buildGenerateSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert AI fashion photographer prompt engineer specializing in Amazon product scene images.\n\n");
        sb.append("Your task: Generate a professional, detailed English prompt for AI image generation to create a scene photo.\n\n");

        // 风格指南
        sb.append("## Core Style Requirements\n");
        sb.append("- ALL prompts MUST be American style, targeting US market\n");
        sb.append("- American lifestyle, natural and authentic\n");
        sb.append("- Avoid Chinese elements, over-retouching, influencer style\n");
        sb.append("- Include: American lifestyle, natural and authentic, confident and empowering\n");
        sb.append("- Include: realistic skin texture, no airbrushing\n\n");

        // 提示词结构
        sb.append("## Prompt Structure\n");
        sb.append("Follow this structure:\n");
        sb.append("[Scene description with specific location and props] + \n");
        sb.append("[Model description: ethnicity, age, body type, wearing clothing] + \n");
        sb.append("[Lighting and atmosphere] + \n");
        sb.append("[Camera/lens parameters] + \n");
        sb.append("[Mood and style keywords]\n\n");

        sb.append("## Anti-Plastic Constraints (always include)\n");
        sb.append("Natural skin texture, visible pores, realistic lighting, no airbrushing, authentic feel\n\n");

        sb.append("## Output Format (CRITICAL: return EXACTLY this JSON structure)\n");
        sb.append("Return a single JSON object with this structure:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"mode\": \"specify\",\n");
        sb.append("  \"scenes\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"Scene Name in English\",\n");
        sb.append("      \"name_cn\": \"场景中文名\",\n");
        sb.append("      \"description\": \"Brief scene description in English\",\n");
        sb.append("      \"prompts\": [\n");
        sb.append("        \"提示词1 - wide shot角度\",\n");
        sb.append("        \"提示词2 - medium shot角度\",\n");
        sb.append("        \"提示词3 - close-up角度\"\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("Rules:\n");
        sb.append("- Return exactly ONE JSON object, NOT an array\n");
        sb.append("- scenes array MUST contain exactly 1 element\n");
        sb.append("- prompts array MUST contain exactly the requested number of prompts\n");
        sb.append("- Each prompt must be from a DIFFERENT angle/composition/vibe (e.g., different camera distance, lighting, or pose)\n");
        sb.append("- ALL prompts must be complete English scene image generation prompts\n");
        sb.append("- Do NOT wrap in markdown code blocks — return PURE JSON only\n");

        return sb.toString();
    }

    /**
     * 调用 Claude API
     */
    private String callClaude(String systemPrompt, String userPrompt) throws IOException {
        ObjectMapper om = objectMapper;
        ObjectNode rootNode = om.createObjectNode();
        rootNode.put("model", "claude-opus-4-7");
        rootNode.put("stream", false);
        rootNode.put("max_tokens", 2000);
        rootNode.put("system", systemPrompt);

        ArrayNode messages = om.createArrayNode();
        ObjectNode userMsg = om.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        rootNode.set("messages", messages);

        String jsonBody = om.writeValueAsString(rootNode);
        log.info("调用 Claude API (场景生成)，请求体大小: {} bytes", jsonBody.length());

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

            JsonNode root = om.readTree(responseBody);

            if (root.has("content") && root.get("content").isArray()) {
                for (JsonNode block : root.get("content")) {
                    if ("text".equals(block.has("type") ? block.get("type").asText() : "")) {
                        return block.get("text").asText().trim();
                    }
                }
            }

            if (root.has("text")) {
                return root.get("text").asText().trim();
            }

            throw new RuntimeException("无法解析 Claude 响应");
        }
    }

    /**
     * 调用 GPT API
     */
    private String callGpt(String systemPrompt, String userPrompt) throws IOException {
        ObjectMapper om = objectMapper;
        ObjectNode rootNode = om.createObjectNode();
        rootNode.put("model", "gpt-5-5");
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
        log.info("调用 GPT API (场景生成)，请求体大小: {} bytes", jsonBody.length());

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

            JsonNode root = om.readTree(responseBody);

            if (root.has("output") && root.get("output").isArray()) {
                for (JsonNode item : root.get("output")) {
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

            if (root.has("text")) {
                return root.get("text").asText().trim();
            }

            throw new RuntimeException("无法解析 GPT 响应");
        }
    }
}
