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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.math.BigDecimal;

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

    // KIE 仅接受显式合法画面比例；'auto' 及任何非法值一律兜底为 "1:1"，否则创建任务会直接报
    // "aspect_ratio is not within the range of allowed"。批量换色等老路径始终传具体比例，本兜底与之对齐。
    private static final Set<String> KIE_ALLOWED_ASPECT_RATIOS = Set.of(
            "1:1", "16:9", "9:16", "4:3", "3:4", "4:5", "5:4", "3:2", "2:3", "21:9", "9:21");

    private String normalizeAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.trim().isEmpty()) {
            return "1:1";
        }
        String ar = aspectRatio.trim();
        if (KIE_ALLOWED_ASPECT_RATIOS.contains(ar)) {
            return ar;
        }
        return "1:1";
    }

    public KieClientServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * 创建 KIE 任务
     */
    @Override
    public String createTask(String spu, String prompt, String resolution, String aspectRatio, String model, String inputUrl, String colorUrl, String callBackUrl) {
        String url = appProperties.getKie().getBaseUrl() + "/jobs/createTask";
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", model);
        if (callBackUrl != null && !callBackUrl.isBlank()) {
            rootNode.put("callBackUrl", callBackUrl.trim());
        }

        ObjectNode inputNode = objectMapper.createObjectNode();
        inputNode.put("prompt", prompt);
        ArrayNode imageArray = objectMapper.createArrayNode();
        appendImageUrls(imageArray, inputUrl);
        appendImageUrls(imageArray, colorUrl);
        if ("gpt-image-2-image-to-image".equals(model)) {
            inputNode.set("input_urls", imageArray);
        } else {
            inputNode.set("image_input", imageArray);
        }
        String normalizedAspectRatio = normalizeAspectRatio(aspectRatio);
        String normalizedResolution = resolution != null && !resolution.trim().isEmpty() ? resolution.trim() : "2K";
        inputNode.put("aspect_ratio", normalizedAspectRatio);
        inputNode.put("resolution", normalizedResolution);
        inputNode.put("output_format", "png");
        rootNode.set("input", inputNode);

        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(rootNode);
        } catch (IOException e) {
            throw new RuntimeException("KIE 请求参数序列化失败: " + e.getMessage(), e);
        }

        RuntimeException lastError = null;
        // KIE may return HTTP 200 with business code 500 and no taskId. In that
        // case the task was not accepted, so retrying the same request is safe.
        for (int attempt = 1; attempt <= 3; attempt++) {
            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .post(body)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    lastError = new RuntimeException("KIE 图片任务创建失败：HTTP " + response.code() + "，" + responseBody);
                    if (response.code() < 500 || attempt == 3) throw lastError;
                    log.warn("KIE 图片任务创建返回 HTTP {}，准备第 {}/3 次重试。model={}, resolution={}, aspectRatio={}, refs={}",
                            response.code(), attempt + 1, model, normalizedResolution, normalizedAspectRatio, imageArray.size());
                } else {
                    JsonNode root = objectMapper.readTree(responseBody);
                    int code = root.has("code") ? root.path("code").asInt(200) : 200;
                    String message = root.path("msg").asText("");
                    JsonNode data = root.path("data");
                    if (code == 200 && data.hasNonNull("taskId")) {
                        return data.path("taskId").asText();
                    }

                    String detail = !message.isBlank() ? message : responseBody;
                    lastError = new RuntimeException("KIE 图片任务未受理（业务码 " + code + "）：" + detail);
                    if (code < 500 || attempt == 3) throw lastError;
                    log.warn("KIE 图片任务返回业务码 {}，准备第 {}/3 次重试。model={}, resolution={}, aspectRatio={}, refs={}, response={}",
                            code, attempt + 1, model, normalizedResolution, normalizedAspectRatio, imageArray.size(), responseBody);
                }
            } catch (IOException e) {
                lastError = new RuntimeException("KIE 图片任务网络请求异常: " + e.getMessage(), e);
                if (attempt == 3) throw lastError;
                log.warn("KIE 图片任务网络异常，准备第 {}/3 次重试。model={}, error={}", attempt + 1, model, e.getMessage());
            }
            pauseBeforeRetry(attempt);
        }
        throw lastError == null ? new RuntimeException("KIE 图片任务创建失败") : lastError;
    }

    private void appendImageUrls(ArrayNode imageArray, String csvUrls) {
        if (csvUrls == null || csvUrls.trim().isEmpty()) return;
        for (String url : csvUrls.split(",")) {
            if (!url.trim().isEmpty()) imageArray.add(url.trim());
        }
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("KIE 图片任务重试被中断", e);
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
                    resultBuilder.status("SUCCESS"); // 🔴 修复1：覆盖原有的状态，统一为大写 SUCCESS

                    // Seedance 2.5 may return both the generated video and a
                    // requested final frame. Preserve the complete ordered list.
                    List<String> resultUrls = parseUrlsFromData(dataNode);
                    resultBuilder.resultUrls(resultUrls);
                    resultBuilder.resultUrl(resultUrls.isEmpty() ? null : resultUrls.get(0));

                    // 解析 KIE 真实费用（兼容多种字段名，取第一个非空数值；data 内取不到再查 root）
                    BigDecimal cost = extractCost(dataNode);
                    if (cost == null) cost = extractCost(root);
                    resultBuilder.cost(cost);

                    // 解析毫秒级完成时间
                    if (dataNode.has("completeTime")) {
                        resultBuilder.completeTime(dataNode.get("completeTime").asLong());
                    }
                } else if ("fail".equals(state) || "failed".equals(state)) {
                    resultBuilder.success(false);
                    resultBuilder.status("FAILED"); // 🔴 修复2：强制将底层的 "fail" 转换为标准大写 "FAILED"

                    String failMsg = dataNode.has("failMsg") ? dataNode.get("failMsg").asText() : "生成失败";
                    resultBuilder.errorMessage(failMsg);
                }

                return resultBuilder.build();
            }
        } catch (Exception e) {
            log.error("查询 KIE 任务详情崩溃: {}", e.getMessage(), e);
            // 🔴 修复3：异常兜底时必须显式赋给 status("FAILED")，否则外层会判断为 null 跳过处理
            return KieTaskResult.builder().taskId(taskId).status("FAILED").finished(true).success(false).errorMessage(e.getMessage()).build();
        }
    }


    /**
     * 解析 KIE 返回的费用字段（兼容多种命名，取第一个非空数值）
     */
    private BigDecimal extractCost(com.fasterxml.jackson.databind.JsonNode data) {
        if (data == null || data.isNull()) return null;
        String[] keys = {"cost", "fee", "amount", "price", "estimatedCost", "totalFee", "costAmount", "charge", "expenses", "totalCost", "costFee", "creditsConsumed", "consumedCredits", "credit", "credits", "consumeCredits", "pointsConsumed", "pointConsumed"};
        for (String k : keys) {
            JsonNode n = data.get(k);
            if (n != null && !n.isNull()) {
                if (n.isNumber()) return n.decimalValue();
                String s = n.asText().trim();
                if (!s.isEmpty()) {
                    try {
                        return new BigDecimal(s.replaceAll("[^0-9.\\-]", ""));
                    } catch (Exception ignore) {
                        // 忽略无法解析的值，继续尝试下一个字段
                    }
                }
            }
        }
        return null;
    }

    /**
     * 按照 KIE 官方最新文档规范提取结果链接
     */
    private String parseUrlFromData(com.fasterxml.jackson.databind.JsonNode data) {
        List<String> urls = parseUrlsFromData(data);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private List<String> parseUrlsFromData(com.fasterxml.jackson.databind.JsonNode data) {
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        if (data == null || data.isEmpty()) return List.of();

        // 兼容 data 是对象或数组的情况
        com.fasterxml.jackson.databind.JsonNode resultObj = data.isObject() ? data : data.get(0);
        if (resultObj == null) return List.of();

        // 🔴 严格按照官方标准：解析 resultJson 字段
        if (resultObj.has("resultJson") && !resultObj.get("resultJson").isNull()) {
            try {
                // 1. 获取套娃的 JSON 字符串
                String innerJsonStr = resultObj.get("resultJson").asText();

                // 2. 将字符串反序列化为 JSON 树
                com.fasterxml.jackson.databind.JsonNode innerNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(innerJsonStr);

                collectResultUrls(innerNode, urls);
            } catch (Exception e) {
                log.warn("❌ 解析 KIE 标准 resultJson 格式失败: {}", e.getMessage());
            }
        }

        collectResultUrls(resultObj, urls);
        return List.copyOf(urls);
    }

    private void collectResultUrls(com.fasterxml.jackson.databind.JsonNode node, java.util.LinkedHashSet<String> urls) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith("http://") || value.startsWith("https://")) urls.add(value);
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectResultUrls(item, urls));
            return;
        }
        for (String key : List.of("resultUrls", "urls", "lastFrameUrl", "last_frame_url", "video_url", "videoUrl", "image_url", "imageUrl", "url")) {
            if (node.has(key)) collectResultUrls(node.get(key), urls);
        }
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
        String callbackUrl = appProperties.getKie().getCallbackUrl();
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            request.setCallBackUrl(callbackUrl.trim());
        }
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
