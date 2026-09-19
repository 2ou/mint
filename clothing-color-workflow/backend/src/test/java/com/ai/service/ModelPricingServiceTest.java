package com.ai.service;

import com.ai.entity.ModelPriceRule;
import com.ai.entity.ModelPriceVersion;
import com.ai.repository.ModelPriceRuleRepository;
import com.ai.repository.ModelPriceVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelPricingServiceTest {

    @Test
    void gptImage25LogicalModelQuotesTheFinalRoutedEndpoint() {
        ModelPriceVersion version = new ModelPriceVersion();
        version.setId(1L);
        version.setVersionCode("CURRENT");

        ModelPriceRule textRule = imageRule(21L, KieImageModels.GPT_IMAGE_25_TEXT, "2K", "0.3200");
        ModelPriceRule imageRule = imageRule(22L, KieImageModels.GPT_IMAGE_25_IMAGE, "2K", "0.3200");
        ModelPriceVersionRepository versions = mock(ModelPriceVersionRepository.class);
        ModelPriceRuleRepository rules = mock(ModelPriceRuleRepository.class);
        when(versions.findFirstByStatusOrderByPublishedAtDesc("PUBLISHED")).thenReturn(Optional.of(version));
        when(rules.findByVersion_IdAndActiveTrueOrderByPriorityDescIdAsc(1L)).thenReturn(List.of(textRule, imageRule));
        ModelPricingService service = new ModelPricingService(versions, rules, new ObjectMapper());

        ModelPricingService.PriceQuote textQuote = service.quote("image", Map.of(
                "model", KieImageModels.GPT_IMAGE_25,
                "resolution", "2K"
        ), 1);
        ModelPricingService.PriceQuote imageQuote = service.quote("image", Map.of(
                "model", KieImageModels.GPT_IMAGE_25,
                "resolution", "2K",
                "reference_images", List.of("https://example.test/ref.png")
        ), 1);

        assertThat(textQuote.model()).isEqualTo(KieImageModels.GPT_IMAGE_25_TEXT);
        assertThat(imageQuote.model()).isEqualTo(KieImageModels.GPT_IMAGE_25_IMAGE);
        assertThat(textQuote.amountCny()).isEqualByComparingTo("0.3200");
        assertThat(imageQuote.amountCny()).isEqualByComparingTo("0.3200");
    }

    private ModelPriceRule imageRule(Long id, String model, String resolution, String price) {
        ModelPriceRule rule = new ModelPriceRule();
        rule.setId(id);
        rule.setMediaType("image");
        rule.setModel(model);
        rule.setResolution(resolution);
        rule.setInputMode("");
        rule.setRateUnit("PER_IMAGE");
        rule.setUnitPriceCny(new BigDecimal(price));
        rule.setBasePriceCny(BigDecimal.ZERO);
        rule.setPriority(400);
        rule.setActive(true);
        rule.setDisplayName(model);
        return rule;
    }

    @Test
    void seedance25VideoEditBillsSourceAndSameLengthOutputSeparately() {
        ModelPriceVersion version = new ModelPriceVersion();
        version.setId(1L);
        version.setVersionCode("CURRENT");

        ModelPriceRule rule = new ModelPriceRule();
        rule.setId(11L);
        rule.setMediaType("video");
        rule.setModel("bytedance/seedance-2-5");
        rule.setResolution("1080p");
        rule.setInputMode("video");
        rule.setRateUnit("PER_SECOND");
        rule.setUnitPriceCny(new BigDecimal("2.4660"));
        rule.setBasePriceCny(BigDecimal.ZERO);
        rule.setPriority(200);
        rule.setActive(true);
        rule.setDisplayName("Seedance 2.5 · 1080P · 视频参考");

        ModelPriceVersionRepository versionRepository = mock(ModelPriceVersionRepository.class);
        ModelPriceRuleRepository ruleRepository = mock(ModelPriceRuleRepository.class);
        when(versionRepository.findFirstByStatusOrderByPublishedAtDesc("PUBLISHED")).thenReturn(Optional.of(version));
        when(ruleRepository.findByVersion_IdAndActiveTrueOrderByPriorityDescIdAsc(1L)).thenReturn(List.of(rule));
        ModelPricingService service = new ModelPricingService(versionRepository, ruleRepository, new ObjectMapper());

        ModelPricingService.PriceQuote quote = service.quote("video", Map.of(
                "model", "bytedance/seedance-2-5",
                "seedance_mode", "video_edit",
                "resolution", "1080p",
                "duration", -1,
                "aspect_ratio", "adaptive",
                "reference_video_urls", List.of("https://example.test/source.mp4"),
                "video_duration_seconds", 11
        ), 1);

        assertThat(quote.units()).isEqualByComparingTo("22");
        assertThat(quote.amountCny()).isEqualByComparingTo("54.2520");
        assertThat(quote.components()).hasSize(2);
        assertThat(quote.components().get(0).displayName()).contains("源视频 11 秒");
        assertThat(quote.components().get(0).amountCny()).isEqualByComparingTo("27.1260");
        assertThat(quote.components().get(1).displayName()).contains("等长输出 11 秒");
        assertThat(quote.components().get(1).amountCny()).isEqualByComparingTo("27.1260");
    }

    @Test
    void ordinaryVideoReferenceDoesNotUseSeedance25VideoEditFormula() {
        ModelPriceVersion version = new ModelPriceVersion();
        version.setId(1L);
        version.setVersionCode("CURRENT");

        ModelPriceRule rule = new ModelPriceRule();
        rule.setId(12L);
        rule.setMediaType("video");
        rule.setModel("bytedance/seedance-2");
        rule.setResolution("1080p");
        rule.setInputMode("video");
        rule.setRateUnit("PER_SECOND");
        rule.setUnitPriceCny(new BigDecimal("1.9800"));
        rule.setBasePriceCny(BigDecimal.ZERO);
        rule.setPriority(200);
        rule.setActive(true);
        rule.setDisplayName("Seedance 2 · 1080P · 视频参考");

        ModelPriceVersionRepository versionRepository = mock(ModelPriceVersionRepository.class);
        ModelPriceRuleRepository ruleRepository = mock(ModelPriceRuleRepository.class);
        when(versionRepository.findFirstByStatusOrderByPublishedAtDesc("PUBLISHED")).thenReturn(Optional.of(version));
        when(ruleRepository.findByVersion_IdAndActiveTrueOrderByPriorityDescIdAsc(1L)).thenReturn(List.of(rule));
        ModelPricingService service = new ModelPricingService(versionRepository, ruleRepository, new ObjectMapper());

        ModelPricingService.PriceQuote quote = service.quote("video", Map.of(
                "model", "bytedance/seedance-2",
                "resolution", "1080p",
                "reference_video_urls", List.of("https://example.test/source.mp4"),
                "video_duration_seconds", 11
        ), 1);

        assertThat(quote.units()).isEqualByComparingTo("11");
        assertThat(quote.amountCny()).isEqualByComparingTo("21.7800");
        assertThat(quote.components()).hasSize(1);
    }
}
