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
}
