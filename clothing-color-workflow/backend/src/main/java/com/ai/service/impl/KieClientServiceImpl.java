package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.dto.KieCreateTaskRequest;
import com.ai.dto.KieTaskResult;
import com.ai.exception.BusinessException;
import com.ai.service.KieClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class KieClientServiceImpl implements KieClientService {

    // 🔴 补充 4：初始化 Gson，用于 JSON 的序列化和反序列化
    private final Gson gson = new Gson();

    // 🔴 补充 5：初始化 OkHttpClient 用于发送 HTTP 请求（如果之前有写过就不用加）
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KieClientServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 创建 KIE 任务
     */
    @Override
    public String createTask(String spu, String prompt, String resolution, String model, String inputUrl, String colorUrl) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/jobs/createTask";

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", model);

            ObjectNode inputNode = objectMapper.createObjectNode();
            inputNode.put("prompt", prompt);

            ArrayNode imageArray = objectMapper.createArrayNode();

            // 1. 处理原图 (支持逗号分隔的多图)
            if (inputUrl != null && !inputUrl.trim().isEmpty()) {
                String[] urls = inputUrl.split(",");
                for (String u : urls) {
                    if (!u.trim().isEmpty()) {
                        imageArray.add(u.trim());
                    }
                }
            }

            // 2. 处理颜色图/参考图
            if (colorUrl != null && !colorUrl.trim().isEmpty()) {
                imageArray.add(colorUrl.trim());
            }

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

    /**
     * 获取完整结果对象（包含状态、URL、完成时间等）
     */
    @Override
    public KieTaskResult getFullResult(String taskId) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/jobs/recordInfo?taskId=" + taskId;

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.info("【KIE 详情查询】taskId: {}, 返回报文: {}", taskId, responseBody);

                if (!response.isSuccessful()) {
                    return KieTaskResult.builder().taskId(taskId).finished(false).build();
                }

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataNode = root.get("data");

                if (dataNode == null || dataNode.isNull()) {
                    return KieTaskResult.builder().taskId(taskId).finished(false).build();
                }

                String state = dataNode.has("state") ? dataNode.get("state").asText().toLowerCase() : "";

                // 构造返回 DTO
                KieTaskResult.KieTaskResultBuilder resultBuilder = KieTaskResult.builder()
                        .taskId(taskId)
                        .status(state)
                        .finished("success".equals(state) || "fail".equals(state));

                if ("success".equals(state)) {
                    resultBuilder.success(true);
                    // 解析图片 URL
                    resultBuilder.resultUrl(parseUrlFromData(dataNode));

                    // 🔴 解析毫秒级完成时间
                    if (dataNode.has("completeTime")) {
                        resultBuilder.completeTime(dataNode.get("completeTime").asLong());
                    }
                } else if ("fail".equals(state) || "failed".equals(state)) {
                    resultBuilder.success(false);
                    String failMsg = dataNode.has("failMsg") ? dataNode.get("failMsg").asText() : "生成失败";
                    resultBuilder.errorMessage(failMsg);
                }

                return resultBuilder.build();
            }
        } catch (Exception e) {
            log.error("查询 KIE 任务详情崩溃: {}", e.getMessage(), e);
            return KieTaskResult.builder().taskId(taskId).finished(true).success(false).errorMessage(e.getMessage()).build();
        }
    }


    /**
     * 按照 KIE 官方最新文档规范提取结果链接
     */
    private String parseUrlFromData(com.fasterxml.jackson.databind.JsonNode data) {
        if (data == null || data.isEmpty()) return null;

        // 兼容 data 是对象或数组的情况
        com.fasterxml.jackson.databind.JsonNode resultObj = data.isObject() ? data : data.get(0);
        if (resultObj == null) return null;

        // 🔴 严格按照官方标准：解析 resultJson 字段
        if (resultObj.has("resultJson") && !resultObj.get("resultJson").isNull()) {
            try {
                // 1. 获取套娃的 JSON 字符串
                String innerJsonStr = resultObj.get("resultJson").asText();

                // 2. 将字符串反序列化为 JSON 树
                com.fasterxml.jackson.databind.JsonNode innerNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(innerJsonStr);

                // 3. 严格提取 resultUrls 数组中的第一个链接（无论是图片还是视频）
                if (innerNode.has("resultUrls") && innerNode.get("resultUrls").isArray() && innerNode.get("resultUrls").size() > 0) {
                    return innerNode.get("resultUrls").get(0).asText();
                }
            } catch (Exception e) {
                log.warn("❌ 解析 KIE 标准 resultJson 格式失败: {}", e.getMessage());
            }
        }

        // ================= 兜底防御区 =================
        // 虽然官方说统一了，但以防万一某些老模型没迁移完，保留几个最基础的兜底
        if (resultObj.has("video_url") && !resultObj.get("video_url").isNull()) return resultObj.get("video_url").asText();
        if (resultObj.has("imageUrl") && !resultObj.get("imageUrl").isNull()) return resultObj.get("imageUrl").asText();
        if (resultObj.has("url") && !resultObj.get("url").isNull()) return resultObj.get("url").asText();
        // ==============================================

        return null;
    }

    @Override
    public String getRawResult(String taskId) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/jobs/recordInfo?taskId=" + taskId;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.body() != null ? response.body().string() : "{}";
            }
        } catch (Exception e) {
            log.error("直接获取 KIE 原生报文失败", e);
            return "{\"error\": \"获取失败: " + e.getMessage() + "\"}";
        }
    }

    @Override
    public KieTaskResult createVideoTask(String model, Map<String, Object> input) {
        // 🔴 核心修改：从统一配置中实时动态获取
        String apiUrl = appProperties.getKie().getBaseUrl();
        String apiKey = appProperties.getKie().getApiKey();

        // 1. 构造 KIE 任务请求体
        KieCreateTaskRequest request = new KieCreateTaskRequest();
        request.setModel(model);
        request.setCallBackUrl(null); // 纯轮询模式
        request.setInput(input);

        String jsonBody = gson.toJson(request);
        log.info("🚀 [视频任务下发] 模型: {}, 请求内容: {}", model, jsonBody);

        // 2. 构造 OkHttp 请求 (使用动态获取的 apiUrl 和 apiKey)
        okhttp3.Request okRequest = new okhttp3.Request.Builder()
                .url(apiUrl + "/jobs/createTask") // 完美拼接，不会再报 404
                .post(okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        // 3. 执行请求并解析响应
        try (okhttp3.Response response = httpClient.newCall(okRequest).execute()) {
            String resStr = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("❌ KIE 接口响应异常: HTTP {}, 返回内容: {}", response.code(), resStr);
                throw new BusinessException("KIE 接口请求失败，HTTP 状态码: " + response.code());
            }

            log.info("📥 [KIE 响应成功]: {}", resStr);

            // 智能拆包解析 KIE 的嵌套 JSON 结构
            com.google.gson.JsonObject rootObj = com.google.gson.JsonParser.parseString(resStr).getAsJsonObject();

            int code = rootObj.has("code") ? rootObj.get("code").getAsInt() : 200;
            String msg = rootObj.has("msg") ? rootObj.get("msg").getAsString() : "";
            String status = rootObj.has("status") ? rootObj.get("status").getAsString() : msg;

            if (code != 200 && !"success".equalsIgnoreCase(status) && !"success".equalsIgnoreCase(msg)) {
                log.warn("⚠️ KIE 任务受理失败: {}", resStr);
                throw new BusinessException("AI 服务受理失败: " + msg);
            }

            com.google.gson.JsonObject dataObj = rootObj.has("data") && !rootObj.get("data").isJsonNull()
                    ? rootObj.getAsJsonObject("data") : rootObj;

            if (!dataObj.has("taskId") || dataObj.get("taskId").isJsonNull()) {
                log.warn("⚠️ KIE 任务受理异常，找不到 taskId: {}", resStr);
                throw new BusinessException("AI 服务未返回 taskId");
            }

            String taskId = dataObj.get("taskId").getAsString();

            KieTaskResult result = new KieTaskResult();
            result.setTaskId(taskId);
            result.setStatus("SUCCESS");

            return result;

        } catch (java.net.SocketTimeoutException e) {
            log.error("⏳ KIE 接口调用超时: {}", e.getMessage());
            throw new BusinessException("AI 服务响应超时，请稍后在大盘查看任务状态");
        } catch (Exception e) {
            log.error("💥 KIE 视频任务调用发生严重异常", e);
            throw new BusinessException("任务下发异常: " + e.getMessage());
        }
    }
}