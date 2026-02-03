package com.mint.batch.model;

import java.util.List;

public class GenerationRequest {
    private final String model;
    private final String prompt;
    private final List<String> negativePrompts;

    public GenerationRequest(String model, String prompt, List<String> negativePrompts) {
        this.model = model;
        this.prompt = prompt;
        this.negativePrompts = negativePrompts;
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getNegativePrompts() {
        return negativePrompts;
    }
}
