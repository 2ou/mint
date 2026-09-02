package com.ai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the only request shape accepted by Seedance 2.5 in this application.
 *
 * <p>KIE documents three mutually exclusive input scenarios: strict frames,
 * multimodal references, and text only. Keeping that rule here prevents a
 * stale browser or a manually composed request from mixing incompatible
 * fields before it reaches KIE.</p>
 */
@Service
public class Seedance25VideoRequestService {

    public static final String MODEL = "bytedance/seedance-2-5";
    private static final Set<String> MODES = Set.of("text", "first_frame", "first_last_frame", "multimodal");
    private static final Set<String> RESOLUTIONS = Set.of("480p", "720p", "1080p");
    private static final Set<String> ASPECT_RATIOS = Set.of("1:1", "4:3", "3:4", "16:9", "9:16", "21:9");
    private static final int MAX_PROMPT_LENGTH = 30_000;
    private static final int MAX_REFERENCE_IMAGES = 30;
    private static final int MAX_REFERENCE_AUDIOS = 10;

    /**
     * Normalizes a UI payload to the documented KIE request. Canvas callers
     * pass {@code true} so their final-frame output is enabled by default;
     * the standalone page passes {@code false}.
     */
    public Map<String, Object> normalize(Map<String, Object> submitted, boolean defaultReturnLastFrame) {
        Map<String, Object> source = submitted == null ? Map.of() : submitted;
        String prompt = requiredText(source.get("prompt"), "提示词为必填项");
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException("提示词不能超过 30000 个字符");
        }

        int duration = requiredDuration(source.get("duration"));
        String resolution = requiredChoice(source.get("resolution"), RESOLUTIONS, "分辨率仅支持 480p、720p 或 1080p");
        String aspectRatio = requiredChoice(source.get("aspect_ratio"), ASPECT_RATIOS,
                "画面比例仅支持 1:1、4:3、3:4、16:9、9:16 或 21:9");

        List<MediaItem> imageItems = mediaItems(source, List.of(
                "reference_image_urls", "images", "imageUrls", "imagesUrl", "imagesUrls", "imageUrl", "image_url",
                "input_image", "image_input"));
        List<String> referenceImages = urls(imageItems);
        List<String> referenceVideos = urls(mediaItems(source, List.of("reference_video_urls", "videos", "videoUrls", "video_url")));
        List<String> referenceAudios = urls(mediaItems(source, List.of("reference_audio_urls", "audios", "audioUrls", "audio_url")));
        List<String> firstFrames = urls(mediaItems(source, List.of("first_frame_url", "firstFrameUrl")));
        List<String> lastFrames = urls(mediaItems(source, List.of("last_frame_url", "lastFrameUrl")));

        for (MediaItem item : imageItems) {
            if ("first_frame".equals(item.role()) && !firstFrames.contains(item.url())) firstFrames.add(item.url());
            if ("last_frame".equals(item.role()) && !lastFrames.contains(item.url())) lastFrames.add(item.url());
        }

        String requestedMode = text(source.get("seedance_mode"));
        String mode = requestedMode.isBlank()
                ? inferMode(firstFrames, lastFrames, referenceImages, referenceVideos, referenceAudios)
                : requestedMode;
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("Seedance 2.5 输入模式无效");
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        input.put("duration", duration);
        input.put("resolution", resolution);
        input.put("aspect_ratio", aspectRatio);
        input.put("generate_audio", booleanValue(source.get("generate_audio"), false));
        input.put("return_last_frame", booleanValue(source.get("return_last_frame"), defaultReturnLastFrame));
        input.put("output_format", "mp4");

        switch (mode) {
            case "text" -> requireNoMedia(firstFrames, lastFrames, referenceImages, referenceVideos, referenceAudios,
                    "文生视频模式不能附带图片、视频或音频素材");
            case "first_frame" -> {
                requireExactlyOne(firstFrames, "首帧模式需要且只能提供一张首帧图");
                requireNoMedia(lastFrames, referenceImages, referenceVideos, referenceAudios,
                        "首帧模式不能混用尾帧或多模态参考素材");
                input.put("first_frame_url", firstFrames.get(0));
            }
            case "first_last_frame" -> {
                requireExactlyOne(firstFrames, "首尾帧模式需要且只能提供一张首帧图");
                requireExactlyOne(lastFrames, "首尾帧模式需要且只能提供一张尾帧图");
                requireNoMedia(referenceImages, referenceVideos, referenceAudios,
                        "首尾帧模式不能混用多模态参考素材");
                input.put("first_frame_url", firstFrames.get(0));
                input.put("last_frame_url", lastFrames.get(0));
            }
            case "multimodal" -> {
                requireNoMedia(firstFrames, lastFrames, "多模态模式不能混用首帧或尾帧");
                if (referenceImages.isEmpty() && referenceVideos.isEmpty() && referenceAudios.isEmpty()) {
                    throw new IllegalArgumentException("多模态模式至少需要一项图片、视频或音频参考素材");
                }
                if (referenceImages.size() > MAX_REFERENCE_IMAGES) {
                    throw new IllegalArgumentException("多模态模式最多支持 30 张参考图片");
                }
                if (referenceAudios.size() > MAX_REFERENCE_AUDIOS) {
                    throw new IllegalArgumentException("多模态模式最多支持 10 个参考音频");
                }
                if (!referenceImages.isEmpty()) input.put("reference_image_urls", referenceImages);
                if (!referenceVideos.isEmpty()) input.put("reference_video_urls", referenceVideos);
                if (!referenceAudios.isEmpty()) input.put("reference_audio_urls", referenceAudios);
            }
            default -> throw new IllegalStateException("未处理的 Seedance 2.5 输入模式");
        }
        return input;
    }

    private String inferMode(List<String> firstFrames, List<String> lastFrames, List<String> images,
                             List<String> videos, List<String> audios) {
        boolean hasStrictFrames = !firstFrames.isEmpty() || !lastFrames.isEmpty();
        boolean hasMultimodal = !images.isEmpty() || !videos.isEmpty() || !audios.isEmpty();
        if (hasStrictFrames && hasMultimodal) {
            throw new IllegalArgumentException("Seedance 2.5 的首尾帧与多模态参考素材不能混用，请明确选择输入模式");
        }
        if (hasMultimodal) return "multimodal";
        if (!lastFrames.isEmpty()) return "first_last_frame";
        if (!firstFrames.isEmpty()) return "first_frame";
        return "text";
    }

    private int requiredDuration(Object raw) {
        if (raw == null || text(raw).isBlank()) throw new IllegalArgumentException("时长为必填项，请选择 4–30 秒");
        try {
            if (raw instanceof Number number && Math.rint(number.doubleValue()) != number.doubleValue()) {
                throw new IllegalArgumentException("Seedance 2.5 时长仅支持 4–30 秒整数");
            }
            int duration = raw instanceof Number number ? number.intValue() : Integer.parseInt(text(raw));
            if (duration < 4 || duration > 30) throw new IllegalArgumentException("Seedance 2.5 时长仅支持 4–30 秒整数");
            return duration;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Seedance 2.5 时长仅支持 4–30 秒整数");
        }
    }

    private String requiredChoice(Object raw, Set<String> allowed, String message) {
        String value = text(raw).toLowerCase(Locale.ROOT);
        if (!allowed.contains(value)) throw new IllegalArgumentException(message);
        return value;
    }

    private String requiredText(Object raw, String message) {
        String value = text(raw);
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private void requireExactlyOne(List<String> values, String message) {
        if (values.size() != 1) throw new IllegalArgumentException(message);
    }

    private void requireNoMedia(List<String> first, List<String> second, List<String> third, List<String> fourth, String message) {
        if (!first.isEmpty() || !second.isEmpty() || !third.isEmpty() || !fourth.isEmpty()) throw new IllegalArgumentException(message);
    }

    private void requireNoMedia(List<String> first, List<String> second, List<String> third, String message) {
        if (!first.isEmpty() || !second.isEmpty() || !third.isEmpty()) throw new IllegalArgumentException(message);
    }

    private void requireNoMedia(List<String> first, List<String> second, String message) {
        if (!first.isEmpty() || !second.isEmpty()) throw new IllegalArgumentException(message);
    }

    private void requireNoMedia(List<String> first, List<String> second, List<String> third, List<String> fourth,
                                List<String> fifth, String message) {
        if (!first.isEmpty() || !second.isEmpty() || !third.isEmpty() || !fourth.isEmpty() || !fifth.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private List<MediaItem> mediaItems(Map<String, Object> source, List<String> keys) {
        List<MediaItem> items = new ArrayList<>();
        for (String key : keys) collectMedia(source.get(key), "", items);
        LinkedHashMap<String, MediaItem> unique = new LinkedHashMap<>();
        for (MediaItem item : items) unique.putIfAbsent(item.url(), item);
        return new ArrayList<>(unique.values());
    }

    private void collectMedia(Object raw, String inheritedRole, List<MediaItem> output) {
        if (raw == null) return;
        if (raw instanceof CharSequence || raw instanceof Number) {
            String url = text(raw);
            if (!url.isBlank()) output.add(new MediaItem(url, inheritedRole));
            return;
        }
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> collectMedia(value, inheritedRole, output));
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            String role = text(map.get("role"));
            if (role.isBlank()) role = inheritedRole;
            for (String key : List.of("url", "image_url", "imageUrl", "video_url", "videoUrl", "audio_url", "audioUrl", "src", "output")) {
                if (map.containsKey(key)) collectMedia(map.get(key), role, output);
            }
        }
    }

    private List<String> urls(List<MediaItem> items) {
        return new ArrayList<>(new LinkedHashSet<>(items.stream().map(MediaItem::url).filter(value -> !value.isBlank()).toList()));
    }

    private boolean booleanValue(Object raw, boolean fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Boolean value) return value;
        String value = text(raw).toLowerCase(Locale.ROOT);
        if (value.isBlank()) return fallback;
        return Set.of("true", "1", "yes", "on").contains(value);
    }

    private String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private record MediaItem(String url, String role) {}
}
