package com.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelIdentityContext {
    private Long identityId;
    private String identityName;
    private String modelType;
    private String identityPrompt;
    private String negativePrompt;
    private String referenceImageUrl;
    private String imageModel;
    private String modelVersion;
    private Long seed;
}
