package com.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KieImageModelsTest {

    @Test
    void image25RoutesByPresenceOfReferences() {
        assertThat(KieImageModels.resolveActualModel(KieImageModels.GPT_IMAGE_25, 0))
                .isEqualTo(KieImageModels.GPT_IMAGE_25_TEXT);
        assertThat(KieImageModels.resolveActualModel(KieImageModels.GPT_IMAGE_25, 16))
                .isEqualTo(KieImageModels.GPT_IMAGE_25_IMAGE);
    }

    @Test
    void keepsOnlyTheFineGrainedImage25Model() {
        assertThat(KieImageModels.SELECTABLE_MODELS).contains(KieImageModels.GPT_IMAGE_25);
        assertThat(KieImageModels.isSelectable("gpt-image-2-5-flare")).isFalse();
        assertThat(KieImageModels.isSelectable("gpt-image-2-5-flare-image-to-image")).isFalse();
        assertThat(KieImageModels.isImage25("gpt-image-2-5-flare")).isFalse();
        assertThat(KieImageModels.resolveActualModel("gpt-image-2-5-flare", 1))
                .isEqualTo("gpt-image-2-5-flare");
        assertThat(KieImageModels.displayName(KieImageModels.GPT_IMAGE_25)).isEqualTo("GPT Image 2.5");
        assertThat(KieImageModels.displayName(KieImageModels.GPT_IMAGE_25_IMAGE)).isEqualTo("GPT Image 2.5");
    }

    @Test
    void validatesOfficialImage25Parameters() {
        KieImageModels.Image25Parameters params = KieImageModels.validateImage25(
                KieImageModels.GPT_IMAGE_25, 3, "4k", "21:9", "transparent");

        assertThat(params.actualModel()).isEqualTo(KieImageModels.GPT_IMAGE_25_IMAGE);
        assertThat(params.resolution()).isEqualTo("4K");
        assertThat(params.aspectRatio()).isEqualTo("21:9");
        assertThat(params.background()).isEqualTo("transparent");
    }

    @Test
    void rejectsTooManyReferencesAndOneKOnlyRatiosAtHigherResolution() {
        assertThatThrownBy(() -> KieImageModels.validateImage25(
                KieImageModels.GPT_IMAGE_25, 17, "2K", "1:1", "auto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16");

        assertThatThrownBy(() -> KieImageModels.validateImage25(
                KieImageModels.GPT_IMAGE_25, 0, "4K", "27:16", "auto"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1K");
    }

    @Test
    void validatesKnownReferenceImageFormats() {
        KieImageModels.validateImage25ReferenceFormats(List.of(
                "https://example.test/ref.JPG?token=1",
                "https://example.test/ref.webp"
        ));

        assertThatThrownBy(() -> KieImageModels.validateImage25ReferenceFormats(
                List.of("https://example.test/animated.gif")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG、JPG、PNG、WebP");
    }
}
