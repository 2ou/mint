package com.ai.creative.provider.kie.parser;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class KieResultParserImpl implements KieResultParser {
    @Override
    public String parseStatus(JsonNode node) {
        if (node == null) {
            return "PROCESSING";
        }
        String status = text(node, "status");
        if (status == null) {
            status = text(node.path("data"), "status");
        }
        if (status == null) {
            status = text(node.path("data").path("recordInfo"), "status");
        }
        return status == null ? "PROCESSING" : status.toUpperCase();
    }

    @Override
    public String parseResultUrl(JsonNode node) {
        if (node == null) {
            return null;
        }
        String url = text(node.path("data"), "result_url");
        if (url == null) {
            url = text(node.path("data"), "resultUrl");
        }
        if (url == null) {
            url = text(node.path("data").path("recordInfo"), "result_url");
        }
        if (url == null) {
            url = text(node.path("data").path("recordInfo"), "resultUrl");
        }
        if (url == null) {
            url = text(node, "result_url");
        }
        return url;
    }

    private String text(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
