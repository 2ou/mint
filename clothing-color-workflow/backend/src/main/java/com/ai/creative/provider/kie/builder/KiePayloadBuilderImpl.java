package com.ai.creative.provider.kie.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class KiePayloadBuilderImpl implements KiePayloadBuilder {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JsonNode buildImageToVideoPayload(String modelCode, String inputJson) {
        return build(modelCode, inputJson);
    }

    @Override
    public JsonNode buildVideoToVideoPayload(String modelCode, String inputJson) {
        return build(modelCode, inputJson);
    }

    private JsonNode build(String modelCode, String inputJson) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelCode);
        try {
            root.set("input", objectMapper.readTree(inputJson));
        } catch (Exception e) {
            root.put("input", inputJson);
        }
        return root;
    }
}
