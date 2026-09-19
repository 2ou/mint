package com.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared KIE image-model routing and capability rules. */
public final class KieImageModels {

    public static final String NANO_BANANA_PRO = "nano-banana-pro";
    public static final String NANO_BANANA_2 = "nano-banana-2";
    public static final String GPT_IMAGE_2 = "gpt-image-2-image-to-image";

    /** GPT Image 2.5 只保留精细（sunburst）端点，快速（flare）已下线。 */
    public static final String GPT_IMAGE_25 = "gpt-image-2-5-sunburst";
    public static final String GPT_IMAGE_25_TEXT = "gpt-image-2-5-sunburst-text-to-image";
    public static final String GPT_IMAGE_25_IMAGE = "gpt-image-2-5-sunburst-image-to-image";

    public static final int GPT_IMAGE_25_MAX_REFERENCES = 16;
    public static final long GPT_IMAGE_25_MAX_IMAGE_BYTES = 30L * 1024L * 1024L;

    public static final List<String> SELECTABLE_MODELS = List.of(
            NANO_BANANA_PRO,
            NANO_BANANA_2,
            GPT_IMAGE_2,
            GPT_IMAGE_25
    );

    public static final Set<String> GPT_IMAGE_25_ASPECT_RATIOS = Set.of(
            "auto", "1:1", "3:2", "2:3", "16:9", "9:16", "4:3", "3:4",
            "21:9", "27:16", "16:27", "9:8", "8:9"
    );
    public static final Set<String> GPT_IMAGE_25_ONE_K_ONLY_RATIOS = Set.of("27:16", "16:27", "9:8", "8:9");
    public static final Set<String> GPT_IMAGE_25_BACKGROUNDS = Set.of("auto", "opaque", "transparent");
    private static final Set<String> GPT_IMAGE_25_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> KNOWN_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "tif", "tiff", "avif", "heic", "heif"
    );

    private static final Set<String> GPT_IMAGE_25_MODELS = Set.of(
            GPT_IMAGE_25,
            GPT_IMAGE_25_TEXT,
            GPT_IMAGE_25_IMAGE
    );

    private KieImageModels() {
    }

    public static boolean isImage25(String model) {
        return GPT_IMAGE_25_MODELS.contains(normalize(model));
    }

    public static boolean isSelectable(String model) {
        return SELECTABLE_MODELS.contains(normalize(model));
    }

    public static String requireSelectable(String model, String fallback) {
        String normalized = normalize(model);
        if (normalized.isBlank() || normalized.startsWith("project-")) return fallback;
        if (!isSelectable(normalized)) {
            throw new IllegalArgumentException("不支持的 KIE 图片模型: " + model);
        }
        return normalized;
    }

    public static String resolveActualModel(String requestedModel, int referenceCount) {
        String model = normalize(requestedModel);
        boolean hasReferences = referenceCount > 0;
        if (model.equals(GPT_IMAGE_25)
                || model.equals(GPT_IMAGE_25_TEXT)
                || model.equals(GPT_IMAGE_25_IMAGE)) {
            return hasReferences ? GPT_IMAGE_25_IMAGE : GPT_IMAGE_25_TEXT;
        }
        return model;
    }

    public static int referenceCount(String... csvValues) {
        int count = 0;
        if (csvValues == null) return count;
        for (String csv : csvValues) {
            if (csv == null || csv.isBlank()) continue;
            for (String value : csv.split(",")) {
                if (!value.isBlank()) count += 1;
            }
        }
        return count;
    }

    public static String logicalModel(String model) {
        String normalized = normalize(model);
        if (normalized.equals(GPT_IMAGE_25_TEXT) || normalized.equals(GPT_IMAGE_25_IMAGE)) {
            return GPT_IMAGE_25;
        }
        return normalized;
    }

    public static boolean usesInputUrls(String actualModel) {
        String normalized = normalize(actualModel);
        return GPT_IMAGE_2.equals(normalized)
                || GPT_IMAGE_25_IMAGE.equals(normalized);
    }

    public static Image25Parameters validateImage25(String requestedModel,
                                                     int referenceCount,
                                                     String resolution,
                                                     String aspectRatio,
                                                     String background) {
        if (referenceCount < 0 || referenceCount > GPT_IMAGE_25_MAX_REFERENCES) {
            throw new IllegalArgumentException("GPT Image 2.5 最多支持 16 张参考图");
        }
        String normalizedResolution = normalizeResolution(resolution);
        String normalizedRatio = normalizeAspectRatio(aspectRatio);
        String normalizedBackground = normalizeBackground(background);
        if (!"1K".equals(normalizedResolution) && GPT_IMAGE_25_ONE_K_ONLY_RATIOS.contains(normalizedRatio)) {
            throw new IllegalArgumentException("GPT Image 2.5 的 " + normalizedRatio + " 比例仅支持 1K 分辨率");
        }
        return new Image25Parameters(
                logicalModel(requestedModel),
                resolveActualModel(requestedModel, referenceCount),
                normalizedResolution,
                normalizedRatio,
                normalizedBackground
        );
    }

    /** Rejects only references whose unsupported image format can be determined from the URL. */
    public static void validateImage25ReferenceFormats(Iterable<String> urls) {
        if (urls == null) return;
        for (String rawUrl : urls) {
            String url = normalize(rawUrl);
            if (url.isBlank()) continue;
            if (url.startsWith("data:image/")) {
                int end = url.indexOf(';');
                String subtype = end > 11 ? url.substring(11, end) : "";
                if (!GPT_IMAGE_25_IMAGE_EXTENSIONS.contains(subtype)) {
                    throw unsupportedImageFormat(subtype);
                }
                continue;
            }
            int query = url.indexOf('?');
            if (query >= 0) url = url.substring(0, query);
            int fragment = url.indexOf('#');
            if (fragment >= 0) url = url.substring(0, fragment);
            int dot = url.lastIndexOf('.');
            int slash = url.lastIndexOf('/');
            if (dot <= slash || dot == url.length() - 1) continue;
            String extension = url.substring(dot + 1);
            if (KNOWN_IMAGE_EXTENSIONS.contains(extension)
                    && !GPT_IMAGE_25_IMAGE_EXTENSIONS.contains(extension)) {
                throw unsupportedImageFormat(extension);
            }
        }
    }

    private static IllegalArgumentException unsupportedImageFormat(String format) {
        String suffix = format == null || format.isBlank() ? "" : "（检测到 " + format.toUpperCase(Locale.ROOT) + "）";
        return new IllegalArgumentException("GPT Image 2.5 参考图仅支持 JPEG、JPG、PNG、WebP" + suffix);
    }

    public static String displayName(String model) {
        return switch (logicalModel(model)) {
            case GPT_IMAGE_25 -> "GPT Image 2.5";
            default -> model == null ? "" : model;
        };
    }

    private static String normalizeResolution(String value) {
        String resolution = normalize(value).toUpperCase(Locale.ROOT);
        if (resolution.isBlank()) resolution = "2K";
        if (!Set.of("1K", "2K", "4K").contains(resolution)) {
            throw new IllegalArgumentException("GPT Image 2.5 resolution 仅支持 1K、2K、4K");
        }
        return resolution;
    }

    private static String normalizeAspectRatio(String value) {
        String ratio = normalize(value);
        if (ratio.isBlank()) ratio = "auto";
        if (!GPT_IMAGE_25_ASPECT_RATIOS.contains(ratio)) {
            throw new IllegalArgumentException("GPT Image 2.5 不支持画面比例: " + value);
        }
        return ratio;
    }

    private static String normalizeBackground(String value) {
        String background = normalize(value);
        if (background.isBlank()) background = "auto";
        if (!GPT_IMAGE_25_BACKGROUNDS.contains(background)) {
            throw new IllegalArgumentException("GPT Image 2.5 background 仅支持 auto、opaque、transparent");
        }
        return background;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Image25Parameters(String requestedModel,
                                    String actualModel,
                                    String resolution,
                                    String aspectRatio,
                                    String background) {
    }
}
