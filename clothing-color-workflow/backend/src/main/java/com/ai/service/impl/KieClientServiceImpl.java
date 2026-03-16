package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.service.KieClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;

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
    public String createTask(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/jobs/createTask";

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", model);

            ObjectNode inputNode = objectMapper.createObjectNode();
            inputNode.put("prompt", prompt);

            // 🔴 核心修复：使用 Jackson 的 ArrayNode 动态构建图片数组
            ArrayNode imageArray = objectMapper.createArrayNode();

            if (inputUrl != null && !inputUrl.trim().isEmpty()) {
                imageArray.add(inputUrl); // 必填的原图
            }

            if (colorUrl != null && !colorUrl.trim().isEmpty()) {
                imageArray.add(colorUrl); // 可选的参考图，只有不为空时才加进去
            }

            // 🔴 将动态构建的数组塞入 inputNode (注意这里用的是 set 方法)
            inputNode.set("image_input", imageArray);

            inputNode.put("aspect_ratio", "auto");
            inputNode.put("resolution", resolution);
            inputNode.put("output_format", "png");

            rootNode.set("input", inputNode);

            String jsonBody = objectMapper.writeValueAsString(rootNode);

            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("KIE 创建任务失败: HTTP " + response.code() + " " + responseBody);
                }

                JsonNode node = objectMapper.readTree(responseBody);
                if (node.has("data") && node.get("data").has("taskId")) {
                    return node.get("data").get("taskId").asText();
                } else {
                    throw new RuntimeException("找不到 taskId: " + responseBody);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("KIE 网络请求异常: " + e.getMessage(), e);
        }
    }

    @Override
    public String getResultUrl(String taskId) {
        try {
            // 1. 按照官方文档拼装 URL 和 GET 参数
            String url = appProperties.getKie().getBaseUrl() + "/jobs/recordInfo?taskId=" + taskId;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    log.warn("KIE 接口请求失败，HTTP 状态码: {}", response.code());
                    return null;
                }

                // 2. 解析第一层外壳 JSON
                JsonNode root = objectMapper.readTree(responseBody);

                // 判断 code 是否为 200
                if (root.has("code") && root.get("code").asInt() != 200) {
                    throw new RuntimeException("KIE 接口返回异常: " + responseBody);
                }

                JsonNode dataNode = root.get("data");
                if (dataNode == null || dataNode.isNull()) {
                    return null; // 还没数据，继续等
                }

                // 3. 判断任务 state 状态
                String state = dataNode.has("state") ? dataNode.get("state").asText().toLowerCase() : "";

                if ("success".equals(state)) {
                    // 4. 提取 resultJson 字符串
                    if (dataNode.has("resultJson") && !dataNode.get("resultJson").isNull()) {
                        String resultJsonStr = dataNode.get("resultJson").asText();

                        // 🔴 核心操作：对 resultJson 进行二次解析 (把字符串转成真正的 JSON 对象)
                        JsonNode resultObj = objectMapper.readTree(resultJsonStr);

                        // 从 resultUrls 数组中拿出第一张图的链接
                        if (resultObj.has("resultUrls") && resultObj.get("resultUrls").isArray() && resultObj.get("resultUrls").size() > 0) {
                            return resultObj.get("resultUrls").get(0).asText();
                        }
                    }
                    throw new RuntimeException("任务成功，但无法解析出图片链接: " + responseBody);

                } else if ("fail".equals(state) || "failed".equals(state)) {
                    // 任务失败，提取失败原因抛出
                    String failMsg = dataNode.has("failMsg") ? dataNode.get("failMsg").asText() : "未知错误";
                    throw new RuntimeException("KIE 生成失败: " + failMsg);
                }

                // 状态不是 success 也不是 fail（通常是 processing 或 waiting 等），直接返回 null 代表还在排队/画图中
                return null;
            }
        } catch (Exception e) {
            log.error("查询 KIE 任务时发生异常", e);
            throw new RuntimeException("查询远端结果异常: " + e.getMessage());
        }
    }
}