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
    private List<AplusReferenceImage> referenceImages;
    private String supplementaryText;

    /** Legacy untyped module references, kept for backward-compatible clients. */
    public String getMergedLegacySupplementaryImageUrl() {
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

    /** All module references, including the newer role-tagged input list. */
    public String getMergedSupplementaryImageUrl() {
        Set<String> urls = new LinkedHashSet<>();
        String legacyUrls = getMergedLegacySupplementaryImageUrl();
        if (legacyUrls != null) {
            for (String url : legacyUrls.split(",")) {
                if (url != null && !url.isBlank()) {
                    urls.add(url.trim());
                }
            }
        }
        if (referenceImages != null && !referenceImages.isEmpty()) {
            referenceImages.stream()
                    .map(AplusReferenceImage::getUrl)
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
