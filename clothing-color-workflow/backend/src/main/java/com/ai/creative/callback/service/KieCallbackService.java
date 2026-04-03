package com.ai.creative.callback.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @deprecated callback mode is disabled, keep for compatibility only.
 */
@Deprecated
public interface KieCallbackService {
    void handleKieCallback(JsonNode callback);
}
