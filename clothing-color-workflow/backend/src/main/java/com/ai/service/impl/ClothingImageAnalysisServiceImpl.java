package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.service.ClothingImageAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothingImageAnalysisServiceImpl implements ClothingImageAnalysisService {

    private static final String GPT_API_URL = KieGptModels.RESPONSES_API_URL;

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public String buildLockedClothingDescription(String clothingImageUrl, String userClothingDescription) {
        String userText = normalize(userClothingDescription);
        if (isBlank(clothingImageUrl)) {
            return !isBlank(userText) ? userText : "fashion clothing";
        }

        String imageUrl = clothingImageUrl.trim();
        try {
            String imageDescription = cache.get(imageUrl);
            if (isBlank(imageDescription)) {
                imageDescription = analyzeImageSafely(imageUrl);
                if (!isBlank(imageDescription)) {
                    cache.put(imageUrl, imageDescription);
                }
            }
            if (isBlank(imageDescription)) {
                return !isBlank(userText) ? userText : "fashion clothing";
            }
            if (!isBlank(userText) && !imageDescription.toLowerCase().contains(userText.toLowerCase())) {
                return "User notes: " + userText + "\nImage-analyzed garment details: " + imageDescription;
            }
            return imageDescription;
        } catch (Exception e) {
            log.warn("[ClothingVision] image analysis unavailable, fallback to user text: {}", e.getMessage());
            return !isBlank(userText) ? userText : "fashion clothing";
        }
    }

    private String analyzeImageSafely(String imageUrl) {
        try {
            return normalize(callGptVision(imageUrl));
        } catch (Exception e) {
            log.warn("[ClothingVision] failed to analyze {}: {}", imageUrl, e.getMessage());
            return "";
        }
    }

    private String callGptVision(String imageUrl) throws IOException {
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", KieGptModels.GPT_5_5);
        rootNode.put("stream", false);

        ObjectNode reasoning = objectMapper.createObjectNode();
        reasoning.put("effort", "low");
        rootNode.set("reasoning", reasoning);

        ArrayNode input = objectMapper.createArrayNode();
        input.add(message("system", """
                You are a senior apparel image analyst for Amazon/Walmart fashion generation workflows.
                Describe only the clothing visible in the image, not the background or photography style.
                Focus on garment identity for image generation: category, components, colors, neckline, sleeve length, hem, fit, fabric appearance, pattern/print, trims, and any details that must be preserved.
                If a human model is visible, mention body/fit context only when relevant to garment fit, for example plus-size relaxed fit. Do not describe face identity.
                Return one concise English paragraph. Do not output JSON, markdown, bullets, or Chinese.
                """));

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode userContent = objectMapper.createArrayNode();
        ObjectNode userText = objectMapper.createObjectNode();
        userText.put("type", "input_text");
        userText.put("text", """
                Analyze this clothing image and write a locked garment description for prompt generation.
                Include exact colors and pattern placement. Do not invent colors, matching sets, sleeves, pants, skirts, or accessories that are not visible.
                """);
        userContent.add(userText);
        ObjectNode image = objectMapper.createObjectNode();
        image.put("type", "input_image");
        image.put("image_url", imageUrl);
        userContent.add(image);
        userMsg.set("content", userContent);
        input.add(userMsg);

        rootNode.set("input", input);

        String jsonBody = objectMapper.writeValueAsString(rootNode);
        log.info("[ClothingVision] calling GPT vision, request size={} bytes", jsonBody.length());

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(GPT_API_URL)
                .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("GPT vision failed: HTTP " + response.code() + " " + responseBody);
            }
            return parseGptText(responseBody);
        }
    }

    private ObjectNode message(String role, String text) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", role);
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "input_text");
        block.put("text", text);
        content.add(block);
        msg.set("content", content);
        return msg;
    }

    private String parseGptText(String responseBody) throws IOException {
        return GptResponseParser.parseTextOrThrow(objectMapper, responseBody, "Unable to parse GPT vision response");
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
