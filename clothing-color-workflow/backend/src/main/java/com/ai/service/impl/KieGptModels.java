package com.ai.service.impl;

import java.util.Locale;

public final class KieGptModels {

    public static final String RESPONSES_API_URL = "https://api.kie.ai/codex/v1/responses";
    public static final String GPT_5_6_SOL = "gpt-5.6-sol";
    public static final String GPT_5_6_TERRA = "gpt-5.6-terra";
    public static final String GPT_5_6_LUNA = "gpt-5.6-luna";
    public static final String DEFAULT_TEXT_MODEL = GPT_5_6_TERRA;

    private KieGptModels() {
    }

    public static String normalizeTextModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_TEXT_MODEL;
        }
        String normalized = model.trim();
        String key = normalized.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "gpt-5.6-sol", "gpt-5-6-sol", "project-text-sol", "sol" -> GPT_5_6_SOL;
            case "gpt-5.6-luna", "gpt-5-6-luna", "project-text-luna", "luna" -> GPT_5_6_LUNA;
            case "gpt-5.6-terra", "gpt-5-6-terra", "project-text-terra", "terra",
                    "gpt", "gpt-5-5", "gpt-5.5", "project-text" -> GPT_5_6_TERRA;
            default -> DEFAULT_TEXT_MODEL;
        };
    }
}
