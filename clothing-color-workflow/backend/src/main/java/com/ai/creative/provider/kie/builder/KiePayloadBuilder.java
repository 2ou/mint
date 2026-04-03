package com.ai.creative.provider.kie.builder;

import com.fasterxml.jackson.databind.JsonNode;

public interface KiePayloadBuilder {
    JsonNode buildImageToVideoPayload(String modelCode, String inputJson);
    JsonNode buildVideoToVideoPayload(String modelCode, String inputJson);
}
