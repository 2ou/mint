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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 场景生成服务实现
 * 场景由 AI 文本模型自由生成，场景库 (skill) 作为提示词辅助知识
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneGeneratorServiceImpl implements SceneGeneratorService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 场景库辅助知识（线程安全 Holder）
     * 用于增强提示词质量，不用于场景匹配/推荐
     */
    private static class SkillKnowledge {
        final Map<String, Object> styleGuide;
        final List<String> promptTemplates;
        final Map<String, Object> atmosphereKeywords;

        SkillKnowledge(Map<String, Object> styleGuide, List<String> promptTemplates, Map<String, Object> atmosphereKeywords) {
            this.styleGuide = styleGuide != null ? styleGuide : new LinkedHashMap<>();
            this.promptTemplates = promptTemplates != null ? promptTemplates : new ArrayList<>();
            this.atmosphereKeywords = atmosphereKeywords != null ? atmosphereKeywords : new LinkedHashMap<>();
        }
    }

    private volatile SkillKnowledge skillKnowledge = new SkillKnowledge(null, null, null);

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String CLAUDE_API_URL = "https://api.kie.ai/claude/v1/messages";
    private static final String GPT_API_URL = "https://api.kie.ai/codex/v1/responses";

    /**
     * 启动时加载场景库 skill
     */
    @PostConstruct
    public void init() {
        loadSkillKnowledge();
    }

    /**
     * 加载场景库辅助知识（styleGuide + promptTemplates + atmosphereKeywords）
     */
    @SuppressWarnings("unchecked")
    private void loadSkillKnowledge() {
        try {
            InputStream is = null;

            // 1. 尝试从文件系统加载
            String configPath = System.getProperty("app.config.path", "docs/skills/scene-library.json");
            Path filePath = Paths.get(configPath);
            if (Files.exists(filePath)) {
                is = Files.newInputStream(filePath);
                log.info("从文件系统加载场景库 skill: {}", filePath.toAbsolutePath());
            }

            // 2. 尝试从 classpath 加载
            if (is == null) {
                ClassPathResource resource = new ClassPathResource("skills/scene-library.json");
                if (resource.exists()) {
                    is = resource.getInputStream();
                    log.info("从 classpath 加载场景库 skill");
                }
            }

            if (is == null) {
                log.warn("未找到场景库配置文件，提示词生成将不包含 skill 辅助知识");
                return;
            }

            Map<String, Object> config = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
            is.close();

            // 提取 styleGuide
            Map<String, Object> styleGuide = (Map<String, Object>) config.get("styleGuide");

            // 提取所有场景的 promptTemplate 作为参考
            List<String> templates = new ArrayList<>();
            List<Map<String, Object>> categories = (List<Map<String, Object>>) config.get("categories");
            if (categories != null) {
                for (Map<String, Object> cat : categories) {
                    List<Map<String, Object>> scenes = (List<Map<String, Object>>) cat.get("scenes");
                    if (scenes != null) {
                        for (Map<String, Object> scene : scenes) {
                            Object tpl = scene.get("promptTemplate");
                            if (tpl != null) {
                                templates.add(tpl.toString());
                            }
                        }
                    }
                }
            }

            // 提取 atmosphereKeywords
            Map<String, Object> atmosphereKeywords = (Map<String, Object>) config.get("atmosphereKeywords");

            // 一次性原子替换
            this.skillKnowledge = new SkillKnowledge(styleGuide, templates, atmosphereKeywords);

            log.info("场景库 skill 加载成功：styleGuide={}, promptTemplates={}, atmosphereKeys={}",
                    styleGuide != null ? "已加载" : "无",
                    templates.size(),
                    atmosphereKeywords != null ? atmosphereKeywords.keySet() : "无");

        } catch (Exception e) {
            log.error("加载场景库 skill 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 重新加载场景库 skill
     */
    public void reloadSkillKnowledge() {
        loadSkillKnowledge();
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
        String userPrompt = "服装描述：" + clothingDesc + "\n\n请推荐 " + count + " 个最适合该服装的拍摄场景，并为每个场景生成1条完整的英文场景图提示词（放在prompts数组中）。\n\n直接返回JSON对象，不要任何其他文字。";

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
    public String generatePrompt(String sceneDesc, String clothingDesc, int count, String textModel) {
        if (count < 1) count = 1;
        if (count > 10) count = 10;
        if (clothingDesc == null || clothingDesc.trim().isEmpty()) {
            clothingDesc = "fashion clothing";
        }
        if (sceneDesc == null || sceneDesc.trim().isEmpty()) {
            throw new BusinessException("请提供场景描述");
        }
        if (textModel == null || textModel.isEmpty()) {
            textModel = "claude";
        }

        String systemPrompt = buildGenerateSystemPrompt();

        StringBuilder userPb = new StringBuilder();
        userPb.append("## 场景描述\n").append(sceneDesc).append("\n\n");
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
     * 构建推荐场景的系统提示词（场景由 AI 自由创意生成，skill 辅助提升质量）
     */
    @SuppressWarnings("unchecked")
    private String buildRecommendSystemPrompt() {
        SkillKnowledge skill = this.skillKnowledge;
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert fashion e-commerce scene advisor for Amazon US market.\n\n");
        sb.append("Your task: Based on the clothing description, recommend 3-5 BEST shooting scenes. You should creatively generate scenes that best showcase the clothing, considering location, lighting, atmosphere, props, and model poses.\n\n");

        // 注入 skill: styleGuide
        if (skill.styleGuide != null && !skill.styleGuide.isEmpty()) {
            sb.append("## Style Guide (from professional skill library)\n");

            Object coreStyle = skill.styleGuide.get("coreStyle");
            if (coreStyle != null) {
                sb.append("- Core style: ").append(coreStyle).append("\n");
            }

            List<String> mustEmphasize = (List<String>) skill.styleGuide.get("mustEmphasize");
            if (mustEmphasize != null && !mustEmphasize.isEmpty()) {
                sb.append("- Must emphasize: ").append(String.join(", ", mustEmphasize)).append("\n");
            }

            List<String> forbiddenElements = (List<String>) skill.styleGuide.get("forbiddenElements");
            if (forbiddenElements != null && !forbiddenElements.isEmpty()) {
                sb.append("- Forbidden elements: ").append(String.join(", ", forbiddenElements)).append("\n");
            }

            Map<String, String> colorPalette = (Map<String, String>) skill.styleGuide.get("colorPalette");
            if (colorPalette != null && !colorPalette.isEmpty()) {
                sb.append("- Color palette: ");
                List<String> palettes = new ArrayList<>();
                colorPalette.forEach((k, v) -> palettes.add(k + " (" + v + ")"));
                sb.append(String.join("; ", palettes)).append("\n");
            }
            sb.append("\n");
        }

        // 注入 skill: atmosphereKeywords（精选参考）
        if (skill.atmosphereKeywords != null && !skill.atmosphereKeywords.isEmpty()) {
            sb.append("## Atmosphere Reference Keywords\n");
            skill.atmosphereKeywords.forEach((category, items) -> {
                sb.append("- ").append(category).append(": ");
                if (items instanceof List) {
                    List<Map<String, String>> kwList = (List<Map<String, String>>) items;
                    List<String> enNames = new ArrayList<>();
                    for (Map<String, String> kw : kwList) {
                        enNames.add(kw.get("en"));
                    }
                    sb.append(String.join(", ", enNames));
                }
                sb.append("\n");
            });
            sb.append("\n");
        }

        // 注入 skill: promptTemplate 示例（精选 5 个）
        if (skill.promptTemplates != null && !skill.promptTemplates.isEmpty()) {
            sb.append("## Prompt Template Examples (reference only, be creative)\n");
            int sampleCount = Math.min(5, skill.promptTemplates.size());
            for (int i = 0; i < sampleCount; i++) {
                sb.append(i + 1).append(". ").append(skill.promptTemplates.get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## Anti-Plastic Constraints (always include)\n");
        sb.append("Natural skin texture, visible pores, realistic lighting, no airbrushing, authentic feel\n\n");

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
     * 构建生成提示词的系统提示词（skill 辅助提升提示词质量）
     */
    @SuppressWarnings("unchecked")
    private String buildGenerateSystemPrompt() {
        SkillKnowledge skill = this.skillKnowledge;
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert AI fashion photographer prompt engineer specializing in Amazon product scene images.\n\n");
        sb.append("Your task: Generate a professional, detailed English prompt for AI image generation to create a scene photo.\n\n");

        // 注入 skill: styleGuide
        if (skill.styleGuide != null && !skill.styleGuide.isEmpty()) {
            sb.append("## Style Guide (from professional skill library)\n");

            Object coreStyle = skill.styleGuide.get("coreStyle");
            if (coreStyle != null) {
                sb.append("- Core style: ").append(coreStyle).append("\n");
            }

            List<String> mustEmphasize = (List<String>) skill.styleGuide.get("mustEmphasize");
            if (mustEmphasize != null && !mustEmphasize.isEmpty()) {
                sb.append("- Must emphasize: ").append(String.join(", ", mustEmphasize)).append("\n");
            }

            List<String> forbiddenElements = (List<String>) skill.styleGuide.get("forbiddenElements");
            if (forbiddenElements != null && !forbiddenElements.isEmpty()) {
                sb.append("- Forbidden elements: ").append(String.join(", ", forbiddenElements)).append("\n");
            }

            Map<String, String> colorPalette = (Map<String, String>) skill.styleGuide.get("colorPalette");
            if (colorPalette != null && !colorPalette.isEmpty()) {
                sb.append("- Color palette: ");
                List<String> palettes = new ArrayList<>();
                colorPalette.forEach((k, v) -> palettes.add(k + " (" + v + ")"));
                sb.append(String.join("; ", palettes)).append("\n");
            }
            sb.append("\n");
        }

        // 注入 skill: promptTemplate 示例（精选 5 个作为结构参考）
        if (skill.promptTemplates != null && !skill.promptTemplates.isEmpty()) {
            sb.append("## Prompt Template Examples (reference structure, adapt to the specific scene)\n");
            int sampleCount = Math.min(5, skill.promptTemplates.size());
            for (int i = 0; i < sampleCount; i++) {
                sb.append(i + 1).append(". ").append(skill.promptTemplates.get(i)).append("\n");
            }
            sb.append("\n");
        }

        // 注入 skill: atmosphereKeywords
        if (skill.atmosphereKeywords != null && !skill.atmosphereKeywords.isEmpty()) {
            sb.append("## Atmosphere Keywords (use to enhance prompt quality)\n");
            skill.atmosphereKeywords.forEach((category, items) -> {
                sb.append("- ").append(category).append(": ");
                if (items instanceof List) {
                    List<Map<String, String>> kwList = (List<Map<String, String>>) items;
                    List<String> enNames = new ArrayList<>();
                    for (Map<String, String> kw : kwList) {
                        enNames.add(kw.get("en"));
                    }
                    sb.append(String.join(", ", enNames));
                }
                sb.append("\n");
            });
            sb.append("\n");
        }

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

            log.warn("Claude API 响应无法解析：content 数组和 text 字段均未找到。响应: {}", responseBody.substring(0, Math.min(500, responseBody.length())));
            throw new RuntimeException("无法解析 Claude 响应");
        }
    }

    /**
     * 调用 GPT API
     */
    private String callGpt(String systemPrompt, String userPrompt) throws IOException {
        ObjectMapper om = objectMapper;
        ObjectNode rootNode = om.createObjectNode();
        rootNode.put("model", "gpt-5-5");  // kie.ai 模型别名，请确认是否正确
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
