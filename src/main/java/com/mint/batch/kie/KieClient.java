package com.mint.batch.kie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mint.batch.config.AppConfig;
import com.mint.batch.model.GenerationRequest;
import com.mint.batch.model.GenerationResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

public class KieClient {
    private final AppConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KieClient(AppConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public byte[] generateImage(String prompt) throws IOException, InterruptedException {
        GenerationRequest request = new GenerationRequest(config.getKieModelId(), prompt, List.of());
        String body = objectMapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.getKieEndpoint()))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + config.getKieApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("KIE API error: " + response.statusCode() + " - " + response.body());
        }
        GenerationResponse generationResponse = objectMapper.readValue(response.body(), GenerationResponse.class);
        if (generationResponse.getImageBase64() != null && !generationResponse.getImageBase64().isBlank()) {
            return Base64.getDecoder().decode(generationResponse.getImageBase64());
        }
        if (generationResponse.getImageUrl() != null && !generationResponse.getImageUrl().isBlank()) {
            return downloadImage(generationResponse.getImageUrl());
        }
        throw new IOException("KIE response missing image data. Response body: " + response.body());
    }

    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 300) {
            throw new IOException("Failed to download image: " + response.statusCode());
        }
        return response.body();
    }
}
