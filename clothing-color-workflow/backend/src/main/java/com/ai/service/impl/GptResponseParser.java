package com.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GptResponseParser {

    private GptResponseParser() {
    }

    static String parseTextOrThrow(ObjectMapper objectMapper, String responseBody, String errorPrefix) throws IOException {
        String text = parseText(objectMapper, responseBody);
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        throw new RuntimeException(errorPrefix + ": " + preview(responseBody));
    }

    static String parseText(ObjectMapper objectMapper, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        List<String> candidates = new ArrayList<>();

        addText(candidates, root.get("output_text"));
        addText(candidates, root.get("text"));

        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                collectOutputItem(candidates, item);
            }
        }

        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray()) {
            for (JsonNode choice : choices) {
                collectKnownContent(candidates, choice.path("message").path("content"));
                collectKnownContent(candidates, choice.path("delta").path("content"));
                addText(candidates, choice.get("text"));
            }
        }

        collectKnownContent(candidates, root.path("message").path("content"));
        collectKnownContent(candidates, root.get("content"));

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static void collectOutputItem(List<String> candidates, JsonNode item) {
        if (item == null || item.isNull() || item.isMissingNode()) {
            return;
        }
        addText(candidates, item.get("output_text"));
        addText(candidates, item.get("text"));
        collectKnownContent(candidates, item.get("content"));
        collectKnownContent(candidates, item.path("message").path("content"));
    }

    private static void collectKnownContent(List<String> candidates, JsonNode content) {
        if (content == null || content.isNull() || content.isMissingNode()) {
            return;
        }
        if (content.isTextual()) {
            addText(candidates, content);
            return;
        }
        if (content.isArray()) {
            for (JsonNode block : content) {
                collectKnownContent(candidates, block);
            }
            return;
        }
        if (content.isObject()) {
            addText(candidates, content.get("output_text"));
            addText(candidates, content.get("text"));
            addText(candidates, content.get("value"));
            collectKnownContent(candidates, content.get("content"));
        }
    }

    private static void addText(List<String> candidates, JsonNode node) {
        if (node != null && node.isTextual()) {
            candidates.add(node.asText());
        }
    }

    private static String preview(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        return responseBody.substring(0, Math.min(500, responseBody.length()));
    }
}
