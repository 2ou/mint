package com.ai.creative.callback.service.impl;

import com.ai.creative.callback.service.KieCallbackService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @deprecated callback mode is disabled, keep for compatibility only.
 */
@Slf4j
@Service
@Deprecated
public class KieCallbackServiceImpl implements KieCallbackService {
    @Override
    public void handleKieCallback(JsonNode callback) {
        log.warn("KIE callback is disabled in polling mode, payload ignored: {}", callback);
    }
}
