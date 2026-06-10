package com.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AplusModuleExtra {
    private String supplementaryImageUrl;
    private List<String> supplementaryImageUrls;
    private String supplementaryText;

    public String getMergedSupplementaryImageUrl() {
        if (supplementaryImageUrl != null && !supplementaryImageUrl.isBlank()) {
            return supplementaryImageUrl.trim();
        }
        if (supplementaryImageUrls == null || supplementaryImageUrls.isEmpty()) {
            return null;
        }
        return supplementaryImageUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }
}
