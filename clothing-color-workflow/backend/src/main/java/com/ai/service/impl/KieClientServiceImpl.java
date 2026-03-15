package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.service.KieClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;

@Service
@Slf4j
public class KieClientServiceImpl implements KieClientService {

    private final AppProperties appProperties;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KieClientServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    // 🔴 1. 方法签名加上 String model
    public String createTask(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/jobs/createTask";

            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode rootNode = mapper.createObjectNode();

            // 🔴 2. 核心修复：直接使用传进来的 model，不要再用 appProperties 去读了！
            rootNode.put("model", model);

            com.fasterxml.jackson.databind.node.ObjectNode inputNode = mapper.createObjectNode();
            inputNode.put("prompt", prompt);

            com.fasterxml.jackson.databind.node.ArrayNode imageArray = mapper.createArrayNode();
            imageArray.add(inputUrl);
            imageArray.add(colorUrl);
            inputNode.set("image_input", imageArray);

            inputNode.put("aspect_ratio", "auto");
            inputNode.put("resolution", resolution);
            inputNode.put("output_format", "png");

            rootNode.set("input", inputNode);

            String jsonBody = mapper.writeValueAsString(rootNode);

            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("KIE 创建任务失败: HTTP " + response.code() + " 详情: " + responseBody);
                }

                JsonNode node = mapper.readTree(responseBody);
                if (node.has("data") && node.get("data").has("taskId")) {
                    return node.get("data").get("taskId").asText();
                } else {
                    throw new RuntimeException("KIE 接口返回值异常，找不到 taskId: " + responseBody);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("KIE 网络请求异常: " + e.getMessage(), e);
        }
    }

    @Override
    public String getResultUrl(String taskId) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/tasks/" + taskId;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return null;
                JsonNode node = objectMapper.readTree(response.body().string());
                boolean finished = node.get("finished").asBoolean(false);
                if (finished && node.has("result_url")) {
                    return node.get("result_url").asText();
                }
            }
        } catch (IOException e) {
            log.warn("KIE 查询任务异常", e);
        }
        return null;
    }
}