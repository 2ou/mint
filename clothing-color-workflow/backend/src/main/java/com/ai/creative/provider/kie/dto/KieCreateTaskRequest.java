package com.ai.creative.provider.kie.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class KieCreateTaskRequest {
    private String model;
    private String callbackUrl;
    private JsonNode input;
}
