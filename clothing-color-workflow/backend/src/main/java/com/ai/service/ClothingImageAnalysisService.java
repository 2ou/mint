package com.ai.service;

/**
 * Analyzes uploaded clothing reference images and returns a prompt-ready
 * garment description for downstream scene/model prompt generation.
 */
public interface ClothingImageAnalysisService {

    /**
     * Build a locked clothing description from the uploaded image and optional
     * user-provided notes. Returns the notes/fallback when image analysis is not
     * available, so callers can continue without breaking the workflow.
     */
    String buildLockedClothingDescription(String clothingImageUrl, String userClothingDescription);
}
