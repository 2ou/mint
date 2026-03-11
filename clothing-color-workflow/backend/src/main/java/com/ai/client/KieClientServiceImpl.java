package com.ai.client;

import com.ai.config.AppProperties;
import com.ai.dto.KieCreateTaskRequest;
import com.ai.dto.KieTaskResult;
import com.ai.exception.BusinessException;
import com.ai.service.KieClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KieClientServiceImpl implements KieClientService {
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppProperties appProperties;

    @Override
    public String createTask(String prompt, String resolution, String inputImageUrl, String colorImageUrl) {
        KieCreateTaskRequest req = KieCreateTaskRequest.builder()
                .model(appProperties.getKie().getModel())
                .input(KieCreateTaskRequest.Input.builder()
                        .prompt(prompt)
                        .image_input(List.of(inputImageUrl, colorImageUrl))
                        .aspect_ratio("3:4")
                        .resolution(resolution)
                        .output_format("png")
                        .build())
                .build();
        try {
            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(req), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(appProperties.getKie().getBaseUrl() + "/api/v1/jobs/createTask")
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .post(body)
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("KIE创建任务失败");
                }
                JsonNode node = objectMapper.readTree(response.body().string());
                String taskId = text(node, "data", "taskId");
                if (taskId == null) taskId = text(node, "taskId");
                if (taskId == null) throw new BusinessException("KIE未返回taskId");
                return taskId;
            }
        } catch (IOException e) {
            throw new BusinessException("KIE创建任务异常: " + e.getMessage());
        }
    }

    @Override
    public KieTaskResult queryTask(String taskId) {
        Request request = new Request.Builder()
                .url(appProperties.getKie().getBaseUrl() + "/api/v1/jobs/recordInfo?taskId=" + taskId)
                .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                .get()
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new BusinessException("KIE查询任务失败");
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode data = root.path("data");
            String status = firstNonNull(
                    data.path("status").asText(null),
                    data.path("state").asText(null)
            );
            String resultUrl = firstNonNull(
                    at(data, "response", "resultUrls", 0),
                    at(data, "resultJson", "resultUrls", 0),
                    at(root, "output", "image_url")
            );
            boolean success = resultUrl != null;
            boolean finished = success || isFinishedStatus(status);
            String error = data.path("errorMessage").asText(null);
            return KieTaskResult.builder().taskId(taskId).status(status).finished(finished).success(success).resultUrl(resultUrl).errorMessage(error).build();
        } catch (IOException e) {
            throw new BusinessException("KIE查询任务异常: " + e.getMessage());
        }
    }

    private boolean isFinishedStatus(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return s.contains("success") || s.contains("fail") || s.contains("done") || s.contains("finish");
    }

    private String text(JsonNode n, String... path) {
        JsonNode cur = n;
        for (String p : path) {
            cur = cur.path(p);
        }
        return cur.isMissingNode() || cur.isNull() ? null : cur.asText();
    }

    private String at(JsonNode n, String p1, String p2, int idx) {
        JsonNode arr = n.path(p1).path(p2);
        if (arr.isArray() && arr.size() > idx) return arr.get(idx).asText(null);
        return null;
    }

    private String at(JsonNode n, String p1, String p2) {
        JsonNode v = n.path(p1).path(p2);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private String firstNonNull(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
