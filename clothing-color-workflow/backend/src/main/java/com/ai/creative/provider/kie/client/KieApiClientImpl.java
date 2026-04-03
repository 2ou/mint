package com.ai.creative.provider.kie.client;

import com.ai.creative.config.CreativeProperties;
import com.ai.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class KieApiClientImpl implements KieApiClient {
    private final CreativeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public JsonNode createTask(JsonNode payload) {
        return post("/v1/tasks", payload);
    }

    @Override
    public JsonNode queryTaskDetail(String providerTaskId) {
        Request request = new Request.Builder()
                .url(properties.getKie().getBaseUrl() + "/v1/tasks/" + providerTaskId)
                .header("Authorization", "Bearer " + properties.getKie().getApiKey())
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "{}" : response.body().string();
            if (!response.isSuccessful()) {
                throw new BusinessException("kie queryTaskDetail failed http=" + response.code() + ", body=" + body);
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("queryTaskDetail failed, taskId={}", providerTaskId, e);
            throw new BusinessException("queryTaskDetail failed: " + e.getMessage());
        }
    }

    private JsonNode post(String path, JsonNode payload) {
        Request request = new Request.Builder()
                .url(properties.getKie().getBaseUrl() + path)
                .header("Authorization", "Bearer " + properties.getKie().getApiKey())
                .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "{}" : response.body().string();
            if (!response.isSuccessful()) {
                throw new BusinessException("kie createTask failed http=" + response.code() + ", body=" + body);
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("kie post failed, path={}", path, e);
            throw new BusinessException("kie request failed: " + e.getMessage());
        }
    }
}
