package com.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KieCreateTaskRequest {
    private String model;
    private Input input;

    @Data
    @Builder
    public static class Input {
        private String prompt;
        private List<String> image_input;
        private String aspect_ratio;
        private String resolution;
        private String output_format;
    }
}
