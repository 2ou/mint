package com.ai.creative.provider.kie.client;

import com.fasterxml.jackson.databind.JsonNode;

public interface KieApiClient {
    JsonNode createTask(JsonNode payload);
    JsonNode queryTaskDetail(String providerTaskId);
}
