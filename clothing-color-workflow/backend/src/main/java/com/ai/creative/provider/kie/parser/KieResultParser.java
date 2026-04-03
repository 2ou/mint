package com.ai.creative.provider.kie.parser;

import com.fasterxml.jackson.databind.JsonNode;

public interface KieResultParser {
    String parseStatus(JsonNode node);
    String parseResultUrl(JsonNode node);
}
