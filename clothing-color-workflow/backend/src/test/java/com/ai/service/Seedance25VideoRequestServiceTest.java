package com.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Seedance25VideoRequestServiceTest {

    private final Seedance25VideoRequestService service = new Seedance25VideoRequestService();

    @Test
    void onlySendsFieldsAllowedByTheSelectedMode() {
        Map<String, Object> input = service.normalize(Map.of(
                "seedance_mode", "first_last_frame",
                "prompt", "A model walks through a sunlit studio.",
                "duration", 8,
                "resolution", "1080p",
                "aspect_ratio", "16:9",
                "first_frame_url", "https://example.test/first.png",
                "last_frame_url", "https://example.test/last.png",
                "generate_audio", false
        ), true);

        assertThat(input).containsEntry("output_format", "mp4")
                .containsEntry("return_last_frame", true)
                .containsEntry("first_frame_url", "https://example.test/first.png")
                .containsEntry("last_frame_url", "https://example.test/last.png")
                .doesNotContainKeys("reference_image_urls", "reference_video_urls", "camerafixed", "web_search");
    }

    @Test
    void textModeOnlySendsGenerationParameters() {
        Map<String, Object> input = service.normalize(Map.of(
                "seedance_mode", "text",
                "prompt", "A satin dress turns slowly in a clean studio.",
                "duration", 8,
                "resolution", "720p",
                "aspect_ratio", "16:9"
        ), false);

        assertThat(input).containsEntry("duration", 8)
                .containsEntry("aspect_ratio", "16:9")
                .doesNotContainKeys("first_frame_url", "last_frame_url", "reference_image_urls", "reference_video_urls", "reference_audio_urls");
    }

    @Test
    void rejectsMixedStrictFramesAndMultimodalReferences() {
        assertThatThrownBy(() -> service.normalize(Map.of(
                "seedance_mode", "first_frame",
                "prompt", "A model walks through a sunlit studio.",
                "duration", 8,
                "resolution", "720p",
                "aspect_ratio", "16:9",
                "first_frame_url", "https://example.test/first.png",
                "reference_image_urls", List.of("https://example.test/reference.png")
        ), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能混用");
    }

    @Test
    void multimodalModeAllowsImagesAndAudioButNotVideo() {
        Map<String, Object> input = service.normalize(Map.of(
                "seedance_mode", "multimodal",
                "prompt", "Keep the product texture and use the supplied music mood.",
                "duration", 10,
                "resolution", "720p",
                "aspect_ratio", "9:16",
                "reference_image_urls", List.of("https://example.test/reference.png"),
                "reference_audio_urls", List.of("https://example.test/music.mp3")
        ), false);

        assertThat(input).containsEntry("duration", 10)
                .containsEntry("aspect_ratio", "9:16")
                .containsKeys("reference_image_urls", "reference_audio_urls")
                .doesNotContainKey("reference_video_urls");
    }

    @Test
    void videoEditForcesAdaptiveRatioAndOutputDurationToFollowSource() {
        Map<String, Object> input = service.normalize(Map.of(
                "seedance_mode", "video_edit",
                "prompt", "Retime this clip to a new outfit.",
                "duration", 10,
                "resolution", "720p",
                "aspect_ratio", "16:9",
                "reference_video_urls", List.of("https://example.test/clip.mp4"),
                "video_duration_seconds", 12
        ), false);

        assertThat(input).containsEntry("aspect_ratio", "adaptive")
                .containsEntry("duration", -1)
                .containsKey("reference_video_urls")
                .doesNotContainKey("video_duration_seconds");
    }

    @Test
    void rejectsVideoEditingOutsideAllowedDuration() {
        assertThatThrownBy(() -> service.normalize(Map.of(
                "seedance_mode", "video_edit",
                "prompt", "Retime this clip.",
                "duration", 10,
                "resolution", "720p",
                "aspect_ratio", "16:9",
                "reference_video_urls", List.of("https://example.test/clip.mp4"),
                "video_duration_seconds", 45
        ), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4–30");
    }

    @Test
    void rejectsAReferenceVideoInTheNonEditingMultimodalMode() {
        assertThatThrownBy(() -> service.normalize(Map.of(
                "seedance_mode", "multimodal",
                "prompt", "Use this reference clip.",
                "duration", 10,
                "resolution", "720p",
                "aspect_ratio", "16:9",
                "reference_video_urls", List.of("https://example.test/clip.mp4")
        ), false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("视频编辑模式");
    }

    @Test
    void infersVideoEditForLegacyRequestsWithReferenceVideo() {
        Map<String, Object> input = service.normalize(Map.of(
                "prompt", "Rework this clip.",
                "duration", 10,
                "resolution", "720p",
                "aspect_ratio", "16:9",
                "reference_video_urls", List.of("https://example.test/clip.mp4"),
                "video_duration_seconds", 12
        ), false);

        assertThat(input).containsEntry("aspect_ratio", "adaptive")
                .containsEntry("duration", -1)
                .containsKey("reference_video_urls");
    }
}
