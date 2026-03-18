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

            // 🔴 核心优化点：动态构建真正的图片数组
            ArrayNode imageArray = objectMapper.createArrayNode();

            // 1. 处理原图 (支持逗号分隔的多图)
            if (inputUrl != null && !inputUrl.trim().isEmpty()) {
                // 将前端传来的 "url1,url2,url3" 拆分为数组并逐一添加
                String[] urls = inputUrl.split(",");
                for (String u : urls) {
                    if (!u.trim().isEmpty()) {
                        imageArray.add(u.trim());
                    }
                }
            }

            // 2. 处理颜色图/参考图 (如果有的话)
            if (colorUrl != null && !colorUrl.trim().isEmpty()) {
                imageArray.add(colorUrl.trim());
            }

            // 🔴 确保 image_input 字段是一个纯净的数组，例如 ["oss_url1", "oss_url2", "oss_url_ref"]
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
            String url = appProperties.getKie().getBaseUrl() + "/jobs/recordInfo?taskId=" + taskId;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // 🔴 1. 完整读取厂商返回的原始报文
                String responseBody = response.body() != null ? response.body().string() : "";

                // 🔴 2. 核心调试：把 KIE 返回的具体内容打印到控制台，不当“瞎子”！
                log.info("【KIE 结果查询】taskId: {}, 返回原始报文: {}", taskId, responseBody);

                if (!response.isSuccessful()) {
                    log.warn("KIE 接口请求失败，HTTP 状态码: {}", response.code());
                    return null;
                }

                JsonNode root = objectMapper.readTree(responseBody);

                if (root.has("code") && root.get("code").asInt() != 200) {
                    throw new RuntimeException("KIE 接口返回异常: " + responseBody);
                }

                JsonNode dataNode = root.get("data");
                if (dataNode == null || dataNode.isNull()) {
                    return null; // 还没数据，继续等
                }

                String state = dataNode.has("state") ? dataNode.get("state").asText().toLowerCase() : "";

                if ("success".equals(state)) {
                    // 🔴 3. 增强解析兼容性（防止 KIE 厂商悄悄改了字段名）

                    // 方案 A: 结果直接放在 data 的 resultUrls 数组里 (新版常见)
                    if (dataNode.has("resultUrls") && dataNode.get("resultUrls").isArray() && dataNode.get("resultUrls").size() > 0) {
                        return dataNode.get("resultUrls").get(0).asText();
                    }

                    // 方案 B: 结果包在 resultJson 字符串里 (你原来的逻辑)
                    if (dataNode.has("resultJson") && !dataNode.get("resultJson").isNull()) {
                        String resultJsonStr = dataNode.get("resultJson").asText();
                        JsonNode resultObj = objectMapper.readTree(resultJsonStr);

                        // B-1: 找 resultUrls 数组
                        if (resultObj.has("resultUrls") && resultObj.get("resultUrls").isArray() && resultObj.get("resultUrls").size() > 0) {
                            return resultObj.get("resultUrls").get(0).asText();
                        }

                        // B-2: 找单数形式的 imageUrl 或 image_url (很多厂商爱用这俩)
                        if (resultObj.has("imageUrl")) return resultObj.get("imageUrl").asText();
                        if (resultObj.has("image_url")) return resultObj.get("image_url").asText();
                        if (resultObj.has("url")) return resultObj.get("url").asText();
                    }

                    throw new RuntimeException("任务显示成功，但代码未能从报文中找到图片 URL，请检查上方控制台打印的 JSON！");

                } else if ("fail".equals(state) || "failed".equals(state)) {
                    String failMsg = dataNode.has("failMsg") ? dataNode.get("failMsg").asText() : "未知错误";
                    throw new RuntimeException("KIE 生成失败: " + failMsg);
                }

                // 其他状态如 processing, queueing，继续等
                return null;
            }
        } catch (Exception e) {
            log.error("查询 KIE 任务时发生异常: {}", e.getMessage(), e);
            throw new RuntimeException("查询远端结果异常: " + e.getMessage());
        }
    }
}