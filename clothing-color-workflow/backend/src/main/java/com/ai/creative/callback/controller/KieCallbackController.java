package com.ai.creative.callback.controller;

import com.ai.creative.callback.service.KieCallbackService;
import com.ai.dto.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @deprecated callback mode is disabled, keep endpoint only for compatibility.
 */
@RestController
@RequestMapping("/api/creative/callback")
@RequiredArgsConstructor
@Deprecated
public class KieCallbackController {
    private final KieCallbackService kieCallbackService;

    @PostMapping("/kie")
    public ApiResponse<Void> callback(@RequestBody JsonNode body){
        kieCallbackService.handleKieCallback(body);
        return ApiResponse.ok("callback disabled, use polling", null);
    }
}
