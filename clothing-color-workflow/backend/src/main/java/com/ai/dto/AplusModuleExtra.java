package com.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Data
public class AplusModuleExtra {
    private String supplementaryImageUrl;
    private List<String> supplementaryImageUrls;
    private String supplementaryText;

    public String getMergedSupplementaryImageUrl() {
        Set<String> urls = new LinkedHashSet<>();
        if (supplementaryImageUrl != null && !supplementaryImageUrl.isBlank()) {
            for (String url : supplementaryImageUrl.split(",")) {
                if (url != null && !url.isBlank()) {
                    urls.add(url.trim());
                }
            }
        }
        if (supplementaryImageUrls != null && !supplementaryImageUrls.isEmpty()) {
            supplementaryImageUrls.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .map(String::trim)
                    .forEach(urls::add);
        }
        if (urls.isEmpty()) {
            return null;
        }
        return String.join(",", new ArrayList<>(urls));
    }
}
