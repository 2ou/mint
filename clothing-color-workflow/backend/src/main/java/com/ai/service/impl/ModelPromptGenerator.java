package com.ai.service.impl;

import com.ai.dto.ModelGenerateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 模特提示词生成引擎
 * 从配置文件读取模板数据，支持热更新
 */
@Service
@Slf4j
public class ModelPromptGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 配置数据
    private Map<String, ModelTypeTemplate> modelTypes = new LinkedHashMap<>();
    private Map<String, String> ethnicityMap = new HashMap<>();
    private Map<String, String> fabricMap = new HashMap<>();
    private String antiPlasticConstraints;

    /**
     * 模特类型模板数据结构
     */
    public static class ModelTypeTemplate {
        public String typeName;
        public String defaultAgeRange;
        public String bodyDescription;
        public String faceDescription;
        public String hairDescription;
        public String expressionDescription;
        public String suitableClothing;
        public String notSuitableClothing;
        public String promptFragment;
        public LensRecommendation lensRecommendation;

        public static class LensRecommendation {
            public String focalLength;
            public String aperture;
            public String lensType;
            public String camera;
        }
    }

    /**
     * 初始化：从配置文件加载数据
     */
    @PostConstruct
    public void init() {
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try {
            // 尝试从 classpath 加载
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("skills/model-types.json");

            // 如果 classpath 没有，尝试从文件系统加载
            if (inputStream == null) {
                String configPath = System.getProperty("app.config.path", "docs/skills/model-types.json");
                java.io.File configFile = new java.io.File(configPath);
                if (configFile.exists()) {
                    inputStream = new java.io.FileInputStream(configFile);
                }
            }

            if (inputStream != null) {
                Map<String, Object> config = objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});

                // 加载模特类型
                Map<String, Object> modelTypesMap = (Map<String, Object>) config.get("modelTypes");
                if (modelTypesMap != null) {
                    for (Map.Entry<String, Object> entry : modelTypesMap.entrySet()) {
                        ModelTypeTemplate template = objectMapper.convertValue(entry.getValue(), ModelTypeTemplate.class);
                        modelTypes.put(entry.getKey(), template);
                    }
                }

                // 加载族裔映射
                ethnicityMap = (Map<String, String>) config.getOrDefault("ethnicityMap", new HashMap<>());

                // 加载面料映射
                fabricMap = (Map<String, String>) config.getOrDefault("fabricMap", new HashMap<>());

                // 加载限制词
                antiPlasticConstraints = (String) config.getOrDefault("antiPlasticConstraints", "");

                log.info("成功加载模特类型配置，共 {} 种类型", modelTypes.size());
                inputStream.close();
            } else {
                log.warn("未找到配置文件，使用默认配置");
                loadDefaultConfig();
            }
        } catch (IOException e) {
            log.error("加载配置文件失败: {}", e.getMessage(), e);
            loadDefaultConfig();
        }
    }

    /**
     * 加载默认配置（硬编码兜底）
     */
    private void loadDefaultConfig() {
        // 保持原有的硬编码作为兜底
        modelTypes.put("Commercial", createDefaultCommercialTemplate());
        ethnicityMap.put("Caucasian", "Caucasian American woman");
        ethnicityMap.put("African American", "African American woman");
        ethnicityMap.put("Latina", "Latina American woman");
        ethnicityMap.put("Asian American", "Asian American woman");
        ethnicityMap.put("Mixed", "Mixed heritage American woman");
        antiPlasticConstraints = "Avoid plastic skin, avoid over-retouching, avoid perfect symmetry, avoid exaggerated features.";
    }

    private ModelTypeTemplate createDefaultCommercialTemplate() {
        ModelTypeTemplate template = new ModelTypeTemplate();
        template.typeName = "Commercial / Catalog";
        template.defaultAgeRange = "30-40";
        template.promptFragment = "healthy athletic-lean American build, subtle natural curves, visible collarbones";
        template.lensRecommendation = new ModelTypeTemplate.LensRecommendation();
        template.lensRecommendation.focalLength = "50mm 或 85mm";
        template.lensRecommendation.aperture = "f/2.8 - f/5.6";
        template.lensRecommendation.lensType = "标准人像";
        template.lensRecommendation.camera = "Canon EOS R5";
        return template;
    }

    /**
     * 重新加载配置（支持热更新）
     */
    public void reloadConfig() {
        modelTypes.clear();
        ethnicityMap.clear();
        fabricMap.clear();
        loadConfig();
        log.info("配置已重新加载");
    }

    /**
     * 生成完整提示词
     */
    public String generatePrompt(ModelGenerateRequest request) {
        String modelType = request.getModelType();
        if (modelType == null || modelType.isEmpty()) {
            modelType = "Commercial";
        }

        ModelTypeTemplate template = modelTypes.get(modelType);
        if (template == null) {
            log.warn("未找到模特类型: {}，使用默认 Commercial", modelType);
            template = modelTypes.get("Commercial");
        }

        if (template == null) {
            return "Error: No model type configured";
        }

        StringBuilder prompt = new StringBuilder();

        // 1. 拍摄方式与构图
        prompt.append("Professional fashion photography, studio lighting, clean white background. ");

        // 2. 模特主体描述
        prompt.append(template.promptFragment);

        // 3. 族裔定制
        String ethnicity = request.getEthnicity();
        if (ethnicity != null && !ethnicity.isEmpty() && ethnicityMap.containsKey(ethnicity)) {
            prompt.append(", ").append(ethnicityMap.get(ethnicity));
        }

        // 4. 年龄定制
        String ageRange = request.getAgeRange();
        if (ageRange != null && !ageRange.isEmpty()) {
            prompt.append(", age ").append(ageRange);
        } else if (template.defaultAgeRange != null) {
            prompt.append(", age ").append(template.defaultAgeRange);
        }

        // 5. 特殊要求
        String specialReq = request.getSpecialRequirements();
        if (specialReq != null && !specialReq.isEmpty()) {
            prompt.append(", ").append(specialReq);
        }

        // 6. 摄影参数
        if (template.lensRecommendation != null) {
            prompt.append(". Shot on ").append(template.lensRecommendation.camera);
            prompt.append(", ").append(template.lensRecommendation.focalLength).append(" lens");
            prompt.append(", ").append(template.lensRecommendation.aperture);
            prompt.append(", soft diffused studio lighting, natural skin texture with visible pores, slight film grain. ");
        }

        // 7. 限制词
        if (antiPlasticConstraints != null && !antiPlasticConstraints.isEmpty()) {
            prompt.append(antiPlasticConstraints);
        }

        return prompt.toString();
    }

    /**
     * 生成带服装描述的提示词
     */
    public String generatePromptWithClothing(ModelGenerateRequest request, String clothingDescription) {
        String basePrompt = generatePrompt(request);

        if (clothingDescription != null && !clothingDescription.isEmpty()) {
            int insertPos = basePrompt.lastIndexOf(antiPlasticConstraints);
            if (insertPos > 0) {
                return basePrompt.substring(0, insertPos) +
                       "Wearing: " + clothingDescription + ". " +
                       basePrompt.substring(insertPos);
            }
        }

        return basePrompt;
    }

    /**
     * 获取所有模特类型
     */
    public Map<String, String> getModelTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        for (Map.Entry<String, ModelTypeTemplate> entry : modelTypes.entrySet()) {
            types.put(entry.getKey(), entry.getValue().typeName);
        }
        return types;
    }

    /**
     * 获取模特类型详细信息
     */
    public ModelTypeTemplate getModelTypeDetail(String modelType) {
        return modelTypes.get(modelType);
    }

    /**
     * 获取适合的服装类型
     */
    public String getSuitableClothing(String modelType) {
        ModelTypeTemplate template = modelTypes.get(modelType);
        return template != null ? template.suitableClothing : "";
    }

    /**
     * 获取不适合的服装类型
     */
    public String getNotSuitableClothing(String modelType) {
        ModelTypeTemplate template = modelTypes.get(modelType);
        return template != null ? template.notSuitableClothing : "";
    }

    /**
     * 获取面料提示词
     */
    public String getFabricPrompt(String fabricType) {
        if (fabricType == null || fabricType.isEmpty()) {
            return "";
        }
        return fabricMap.getOrDefault(fabricType, "");
    }

    /**
     * 获取镜头推荐信息
     */
    public Map<String, String> getLensRecommendation(String modelType) {
        ModelTypeTemplate template = modelTypes.get(modelType);
        if (template == null || template.lensRecommendation == null) {
            return Collections.emptyMap();
        }

        Map<String, String> recommendation = new LinkedHashMap<>();
        recommendation.put("focalLength", template.lensRecommendation.focalLength);
        recommendation.put("aperture", template.lensRecommendation.aperture);
        recommendation.put("lensType", template.lensRecommendation.lensType);
        recommendation.put("camera", template.lensRecommendation.camera);
        return recommendation;
    }

    /**
     * 获取所有面料类型
     */
    public Map<String, String> getAllFabricTypes() {
        return new LinkedHashMap<>(fabricMap);
    }
}
