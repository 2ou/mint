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
    public String createTask(String spu, String prompt, String resolution, String inputUrl, String colorUrl) {
        try {
            String url = appProperties.getKie().getBaseUrl() + "/tasks";
            String jsonBody = String.format("""
                    {
                      "model": "%s",
                      "prompt": "%s",
                      "spu": "%s",
                      "input_url": "%s",
                      "color_url": "%s",
                      "resolution": "%s"
                    }
                    """, appProperties.getKie().getModel(), prompt, spu, inputUrl, colorUrl, resolution);

            RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("KIE 创建任务失败: " + response.code());
                }
                JsonNode node = objectMapper.readTree(response.body().string());
                return node.get("taskId").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("KIE 创建任务异常: " + e.getMessage(), e);
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