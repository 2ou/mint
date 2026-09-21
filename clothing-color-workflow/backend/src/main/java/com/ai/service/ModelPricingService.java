package com.ai.service;

import com.ai.entity.ModelPriceRule;
import com.ai.entity.ModelPriceVersion;
import com.ai.repository.ModelPriceRuleRepository;
import com.ai.repository.ModelPriceVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side model price catalogue. Prices are resolved from the request
 * payload, never accepted from the browser, so a submitted task always owns an
 * auditable quote snapshot. The UI exposes one editable current catalogue.
 */
@Service
@RequiredArgsConstructor
public class ModelPricingService {

    private static final BigDecimal CREDIT_TO_CNY = new BigDecimal("0.032000");
    private static final String PUBLISHED = "PUBLISHED";
    private static final String SEEDANCE_25_MODEL = "bytedance/seedance-2-5";
    private static final String MINIMAX_H3_IMAGE_INPUT_MODEL = "minimax-h3/image-input";
    private static final List<String> MINIMAX_H3_VIDEO_MODELS = List.of(
            "minimax-h3/text-to-video",
            "minimax-h3/image-to-video",
            "minimax-h3/reference-to-video"
    );

    private final ModelPriceVersionRepository versionRepository;
    private final ModelPriceRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @Transactional
    public void seedInitialCatalogue() {
        if (versionRepository.existsByStatus(PUBLISHED)) {
            ensureMiniMaxH3PriceRules();
            ensureGptImage25PriceRules();
            ensureRecraftRemoveBackgroundPriceRule();
            return;
        }

        ModelPriceVersion version = new ModelPriceVersion();
        version.setVersionCode("CURRENT");
        version.setStatus(PUBLISHED);
        version.setCreditToCny(CREDIT_TO_CNY);
        version.setCreatedBy("PINKSIR");
        version.setPublishedBy("PINKSIR");
        version.setPublishedAt(LocalDateTime.now());
        version.setNote("从批量换色、场景和视频模块迁入的初始 KIE 人民币价格");
        version = versionRepository.save(version);

        List<ModelPriceRule> rules = new ArrayList<>();
        // Existing bulk-colour and scene prices.
        rules.add(rule(version, "image", "nano-banana-pro", "4K", "", "PER_IMAGE", "0.4400", 100, "Nano Banana Pro · 4K"));
        rules.add(rule(version, "image", "nano-banana-pro", "", "", "PER_IMAGE", "0.2500", 10, "Nano Banana Pro"));
        rules.add(rule(version, "image", "nano-banana-2", "", "", "PER_IMAGE", "0.2500", 10, "Nano Banana 2"));
        rules.add(rule(version, "image", "gpt-image-2-image-to-image", "4K", "", "PER_IMAGE", "0.4400", 100, "GPT Image 2 · 4K"));
        rules.add(rule(version, "image", "gpt-image-2-image-to-image", "2K", "", "PER_IMAGE", "0.2500", 100, "GPT Image 2 · 2K"));
        rules.add(rule(version, "image", "gpt-image-2-image-to-image", "1K", "", "PER_IMAGE", "0.0900", 100, "GPT Image 2 · 1K"));
        rules.add(rule(version, "image", "recraft/remove-background", "", "", "PER_IMAGE", "0.0320", 500,
                "Recraft · 背景移除"));

        // Seedance 2.5 price is maintained in KIE credits in the old module;
        // the catalogue stores the resulting CNY per billed second.
        rules.add(rule(version, "video", "bytedance/seedance-2-5", "1080p", "video", "PER_SECOND", "2.4660", 200, "Seedance 2.5 · 1080P · 视频参考"));
        rules.add(rule(version, "video", "bytedance/seedance-2-5", "1080p", "", "PER_SECOND", "4.1040", 100, "Seedance 2.5 · 1080P"));
        rules.add(rule(version, "video", "bytedance/seedance-2-5", "720p", "video", "PER_SECOND", "1.3680", 200, "Seedance 2.5 · 720P · 视频参考"));
        rules.add(rule(version, "video", "bytedance/seedance-2-5", "720p", "", "PER_SECOND", "2.2680", 100, "Seedance 2.5 · 720P"));
        rules.add(rule(version, "video", "bytedance/seedance-2-5", "480p", "video", "PER_SECOND", "0.6120", 200, "Seedance 2.5 · 480P · 视频参考"));
        rules.add(rule(version, "video", "bytedance/seedance-2-5", "480p", "", "PER_SECOND", "1.0080", 100, "Seedance 2.5 · 480P"));
        rules.add(rule(version, "video", "bytedance/seedance-2-mini", "720p", "video", "PER_SECOND", "0.4500", 200, "Seedance 2 Mini · 720P · 视频参考"));
        rules.add(rule(version, "video", "bytedance/seedance-2-mini", "720p", "", "PER_SECOND", "0.7380", 100, "Seedance 2 Mini · 720P"));
        rules.add(rule(version, "video", "bytedance/seedance-2-mini", "480p", "video", "PER_SECOND", "0.2160", 200, "Seedance 2 Mini · 480P · 视频参考"));
        rules.add(rule(version, "video", "bytedance/seedance-2-mini", "480p", "", "PER_SECOND", "0.3420", 100, "Seedance 2 Mini · 480P"));
        rules.add(rule(version, "video", "kling-3.0/motion-control", "1080p", "", "PER_SECOND", "0.8600", 100, "Kling 3.0 Motion · 1080P"));
        rules.add(rule(version, "video", "kling-3.0/motion-control", "", "", "PER_SECOND", "0.6400", 10, "Kling 3.0 Motion"));
        rules.add(rule(version, "video", "kling/v3-turbo-image-to-video", "1080p", "", "PER_SECOND", "0.8100", 100, "Kling Turbo · 1080P"));
        rules.add(rule(version, "video", "kling/v3-turbo-image-to-video", "", "", "PER_SECOND", "0.6480", 10, "Kling Turbo"));
        // Existing video module prices, brought under the same versioned
        // catalogue.  Exact KIE settlement will still replace the estimate.
        rules.add(rule(version, "video", "bytedance/seedance-2", "1080p", "video", "PER_SECOND", "1.9800", 200, "Seedance 2 1080P video input"));
        rules.add(rule(version, "video", "bytedance/seedance-2", "1080p", "", "PER_SECOND", "3.2600", 100, "Seedance 2 1080P"));
        rules.add(rule(version, "video", "bytedance/seedance-2", "720p", "video", "PER_SECOND", "0.8000", 200, "Seedance 2 720P video input"));
        rules.add(rule(version, "video", "bytedance/seedance-2", "720p", "", "PER_SECOND", "1.3100", 100, "Seedance 2 720P"));
        rules.add(rule(version, "video", "bytedance/seedance-2", "480p", "video", "PER_SECOND", "0.3600", 200, "Seedance 2 480P video input"));
        rules.add(rule(version, "video", "bytedance/seedance-2", "480p", "", "PER_SECOND", "0.6000", 100, "Seedance 2 480P"));
        rules.add(rule(version, "video", "kling-3.0/video", "4k", "", "PER_SECOND", "2.1400", 200, "Kling 3.0 4K"));
        rules.add(rule(version, "video", "kling-3.0/video", "pro", "audio", "PER_SECOND", "0.8600", 200, "Kling 3.0 Pro audio"));
        rules.add(rule(version, "video", "kling-3.0/video", "pro", "", "PER_SECOND", "0.5700", 100, "Kling 3.0 Pro"));
        rules.add(rule(version, "video", "kling-3.0/video", "standard", "audio", "PER_SECOND", "0.6400", 200, "Kling 3.0 Standard audio"));
        rules.add(rule(version, "video", "kling-3.0/video", "standard", "", "PER_SECOND", "0.4400", 100, "Kling 3.0 Standard"));
        addGptImage25PriceRules(rules, version);
        addMiniMaxH3PriceRules(rules, version);
        ruleRepository.saveAll(rules);
    }

    @Transactional(readOnly = true)
    public PriceQuote quote(String mediaType, Map<String, Object> payload, int quantity) {
        Optional<ModelPriceVersion> optionalVersion = versionRepository.findFirstByStatusOrderByPublishedAtDesc(PUBLISHED);
        if (optionalVersion.isEmpty()) return PriceQuote.unavailable("价格目录尚未发布");

        ModelPriceVersion version = optionalVersion.get();
        String normalizedMediaType = normalize(mediaType);
        String model = normalize(text(payload, "model"));
        if ("image".equals(normalizedMediaType) && KieImageModels.isImage25(model)) {
            model = KieImageModels.resolveActualModel(model, imageReferenceCount(payload));
        }
        String resolution = resolveResolution(normalizedMediaType, payload);
        String inputMode = resolveInputMode(normalizedMediaType, payload);
        int safeQuantity = Math.max(1, quantity);

        Optional<ModelPriceRule> candidate = findBestRule(version, normalizedMediaType, model, resolution, inputMode);

        if (candidate.isEmpty()) {
            return PriceQuote.unavailable(version.getVersionCode(), model, resolution, inputMode,
                    "该模型或规格暂未配置人民币价格，实际以服务商账单为准");
        }

        ModelPriceRule rule = candidate.get();
        boolean seedance25VideoEdit = isSeedance25VideoEdit(model, payload);
        int secondsPerRun = resolveDuration(model, payload);
        BigDecimal units = switch (normalize(rule.getRateUnit())) {
            case "per_second" -> BigDecimal.valueOf(secondsPerRun).multiply(BigDecimal.valueOf(safeQuantity));
            case "per_image", "per_task" -> BigDecimal.valueOf(safeQuantity);
            default -> BigDecimal.valueOf(safeQuantity);
        };
        List<PriceComponent> components = new ArrayList<>();
        if (seedance25VideoEdit && "per_second".equals(normalize(rule.getRateUnit()))) {
            int sourceSeconds = resolveSeedance25VideoEditSourceSeconds(payload);
            BigDecimal legUnits = BigDecimal.valueOf(sourceSeconds).multiply(BigDecimal.valueOf(safeQuantity));
            components.add(new PriceComponent(rule.getId(), seedance25VideoEditComponentName(rule, "源视频", sourceSeconds, safeQuantity),
                    legUnits, priceAmount(rule, legUnits, true)));
            components.add(new PriceComponent(rule.getId(), seedance25VideoEditComponentName(rule, "等长输出", sourceSeconds, safeQuantity),
                    legUnits, priceAmount(rule, legUnits, false)));
        } else {
            components.add(PriceComponent.from(rule, units, priceAmount(rule, units, true)));
        }

        if (isMiniMaxH3VideoModel(model)) {
            int imageInputCount = miniMaxH3ImageInputCount(payload) * safeQuantity;
            if (imageInputCount > 0) {
                findBestRule(version, normalizedMediaType, MINIMAX_H3_IMAGE_INPUT_MODEL, resolution, "image_input")
                        .ifPresent(imageRule -> {
                            BigDecimal imageUnits = BigDecimal.valueOf(imageInputCount);
                            BigDecimal imageAmount = safeDecimal(imageRule.getBasePriceCny())
                                    .add(safeDecimal(imageRule.getUnitPriceCny()).multiply(imageUnits))
                                    .setScale(4, RoundingMode.HALF_UP);
                            components.add(PriceComponent.from(imageRule, imageUnits, imageAmount));
                        });
            }
        }
        BigDecimal totalAmount = components.stream()
                .map(PriceComponent::amountCny)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        String quoteMessage = seedance25VideoEdit
                ? "Seedance 2.5 视频编辑按源视频与等长输出分别计费；实际以服务商账单为准"
                : "预估费用，实际以服务商账单为准";
        return PriceQuote.available(version, rule, model, resolution, inputMode, safeQuantity, units, totalAmount, components, quoteMessage);
    }

    /** Adds KIE's current H3 768P/2K tiers without overwriting a price that was manually maintained. */
    private void ensureMiniMaxH3PriceRules() {
        Optional<ModelPriceVersion> version = versionRepository.findFirstByStatusOrderByPublishedAtDesc(PUBLISHED);
        if (version.isEmpty()) return;
        List<ModelPriceRule> existing = ruleRepository.findByVersion_IdOrderByPriorityDescIdAsc(version.get().getId());
        List<ModelPriceRule> additions = new ArrayList<>();
        addMiniMaxH3PriceRules(additions, version.get());
        List<ModelPriceRule> missing = additions.stream()
                .filter(candidate -> existing.stream().noneMatch(rule -> samePriceRule(rule, candidate)))
                .toList();
        if (!missing.isEmpty()) ruleRepository.saveAll(missing);
    }

    /** Adds the official KIE 1K/2K/4K prices for the GPT Image 2.5 endpoints. */
    private void ensureGptImage25PriceRules() {
        Optional<ModelPriceVersion> version = versionRepository.findFirstByStatusOrderByPublishedAtDesc(PUBLISHED);
        if (version.isEmpty()) return;
        List<ModelPriceRule> existing = ruleRepository.findByVersion_IdOrderByPriorityDescIdAsc(version.get().getId());
        List<ModelPriceRule> additions = new ArrayList<>();
        addGptImage25PriceRules(additions, version.get());
        List<ModelPriceRule> missing = additions.stream()
                .filter(candidate -> existing.stream().noneMatch(rule -> samePriceRule(rule, candidate)))
                .toList();
        if (!missing.isEmpty()) ruleRepository.saveAll(missing);
    }

    /** Adds the Template Lab cutout rule without overwriting an admin-maintained price. */
    private void ensureRecraftRemoveBackgroundPriceRule() {
        Optional<ModelPriceVersion> version = versionRepository.findFirstByStatusOrderByPublishedAtDesc(PUBLISHED);
        if (version.isEmpty()) return;
        List<ModelPriceRule> existing = ruleRepository.findByVersion_IdOrderByPriorityDescIdAsc(version.get().getId());
        boolean present = existing.stream().anyMatch(rule ->
                "image".equals(normalize(rule.getMediaType()))
                        && "recraft/remove-background".equals(normalize(rule.getModel())));
        if (!present) {
            ruleRepository.save(rule(version.get(), "image", "recraft/remove-background", "", "", "PER_IMAGE",
                    "0.0320", 500, "Recraft · 背景移除"));
        }
    }

    private void addGptImage25PriceRules(List<ModelPriceRule> rules, ModelPriceVersion version) {
        Map<String, String> variants = Map.of(
                KieImageModels.GPT_IMAGE_25_TEXT, "GPT Image 2.5 · 文生图",
                KieImageModels.GPT_IMAGE_25_IMAGE, "GPT Image 2.5 · 图生图"
        );
        for (Map.Entry<String, String> variant : variants.entrySet()) {
            rules.add(rule(version, "image", variant.getKey(), "1K", "", "PER_IMAGE", "0.1920", 400,
                    variant.getValue() + " · 1K"));
            rules.add(rule(version, "image", variant.getKey(), "2K", "", "PER_IMAGE", "0.3200", 400,
                    variant.getValue() + " · 2K"));
            rules.add(rule(version, "image", variant.getKey(), "4K", "", "PER_IMAGE", "0.5120", 400,
                    variant.getValue() + " · 4K"));
        }
    }

    private void addMiniMaxH3PriceRules(List<ModelPriceRule> rules, ModelPriceVersion version) {
        for (String model : MINIMAX_H3_VIDEO_MODELS) {
            String mode = model.endsWith("text-to-video") ? "文生视频"
                    : model.endsWith("image-to-video") ? "图生视频"
                    : "多模态参考";
            rules.add(rule(version, "video", model, "768p", "", "PER_SECOND", "0.2560", 300,
                    "MiniMax H3 · " + mode + " · 768P"));
            rules.add(rule(version, "video", model, "2k", "", "PER_SECOND", "0.4160", 300,
                    "MiniMax H3 · " + mode + " · 2K"));
        }
        rules.add(rule(version, "video", MINIMAX_H3_IMAGE_INPUT_MODEL, "768p", "image_input", "PER_IMAGE", "0.1280", 300,
                "MiniMax H3 · 图片输入 · 768P"));
        rules.add(rule(version, "video", MINIMAX_H3_IMAGE_INPUT_MODEL, "2k", "image_input", "PER_IMAGE", "0.1280", 300,
                "MiniMax H3 · 图片输入 · 2K"));
    }

    private Optional<ModelPriceRule> findBestRule(ModelPriceVersion version, String mediaType, String model,
                                                   String resolution, String inputMode) {
        return ruleRepository.findByVersion_IdAndActiveTrueOrderByPriorityDescIdAsc(version.getId())
                .stream()
                .filter(rule -> normalize(rule.getMediaType()).equals(mediaType))
                .filter(rule -> normalize(rule.getModel()).equals(model))
                .filter(rule -> matches(rule.getResolution(), resolution))
                .filter(rule -> matches(rule.getInputMode(), inputMode))
                .max(Comparator.comparingInt(rule -> matchScore(rule, resolution, inputMode)));
    }

    private int imageReferenceCount(Map<String, Object> payload) {
        for (String key : List.of("reference_images", "input_urls", "image_input", "images")) {
            Object value = payload.get(key);
            if (value instanceof List<?> list) return (int) list.stream().filter(java.util.Objects::nonNull).count();
            if (value != null && !String.valueOf(value).isBlank()) {
                return KieImageModels.referenceCount(String.valueOf(value));
            }
        }
        return KieImageModels.referenceCount(
                text(payload, "inputImageUrl"), text(payload, "colorImageUrl"),
                text(payload, "input_url"), text(payload, "color_url"));
    }

    private boolean samePriceRule(ModelPriceRule left, ModelPriceRule right) {
        return normalize(left.getMediaType()).equals(normalize(right.getMediaType()))
                && normalize(left.getModel()).equals(normalize(right.getModel()))
                && normalize(left.getResolution()).equals(normalize(right.getResolution()))
                && normalize(left.getInputMode()).equals(normalize(right.getInputMode()));
    }

    /** KIE returns its settled charge in credits. The UI and ledger expose CNY only. */
    @Transactional(readOnly = true)
    public BigDecimal kieCreditsToCny(BigDecimal credits, String versionCode) {
        if (credits == null) return null;
        BigDecimal rate = versionRepository.findByVersionCode(versionCode)
                .map(ModelPriceVersion::getCreditToCny)
                .filter(value -> value != null && value.signum() >= 0)
                .orElse(CREDIT_TO_CNY);
        return credits.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentCatalogue() {
        ModelPriceVersion version = currentVersion();
        Map<String, Object> catalogue = new LinkedHashMap<>();
        catalogue.put("credit_to_cny", version.getCreditToCny());
        catalogue.put("currency", "CNY");
        return catalogue;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> currentRules() {
        return rules(currentVersion().getId());
    }

    @Transactional
    public List<Map<String, Object>> replaceCurrentRules(List<Map<String, Object>> submittedRules) {
        return replaceRules(currentVersion().getId(), submittedRules);
    }

    @Transactional(readOnly = true)
    private List<Map<String, Object>> rules(Long versionId) {
        return ruleRepository.findByVersion_IdOrderByPriorityDescIdAsc(versionId).stream()
                .map(this::ruleView)
                .toList();
    }

    @Transactional
    private List<Map<String, Object>> replaceRules(Long versionId, List<Map<String, Object>> submittedRules) {
        ModelPriceVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("价格版本不存在"));
        if (!version.getId().equals(currentVersion().getId())) {
            throw new IllegalArgumentException("只能维护当前模型价格目录");
        }
        ruleRepository.deleteByVersion_Id(versionId);
        List<ModelPriceRule> rules = new ArrayList<>();
        for (Map<String, Object> source : submittedRules == null ? List.<Map<String, Object>>of() : submittedRules) {
            String model = text(source, "model");
            String mediaType = text(source, "media_type");
            if (model.isBlank() || mediaType.isBlank()) continue;
            ModelPriceRule rule = new ModelPriceRule();
            rule.setVersion(version);
            rule.setProvider(blankOr(text(source, "provider"), "kie"));
            rule.setMediaType(mediaType.trim().toLowerCase(Locale.ROOT));
            rule.setModel(model.trim());
            rule.setResolution(text(source, "resolution").trim());
            rule.setInputMode(text(source, "input_mode").trim().toLowerCase(Locale.ROOT));
            rule.setRateUnit(blankOr(text(source, "rate_unit"), "PER_TASK").trim().toUpperCase(Locale.ROOT));
            rule.setUnitPriceCny(decimal(source.get("unit_price_cny")));
            rule.setBasePriceCny(decimal(source.get("base_price_cny")));
            rule.setPriority(integer(source.get("priority"), 0));
            rule.setActive(!source.containsKey("active") || Boolean.parseBoolean(String.valueOf(source.get("active"))));
            rule.setDisplayName(text(source, "display_name"));
            rules.add(rule);
        }
        return ruleRepository.saveAll(rules).stream().map(this::ruleView).toList();
    }

    private ModelPriceVersion currentVersion() {
        return versionRepository.findFirstByStatusOrderByPublishedAtDesc(PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("Model price catalogue is not initialized"));
    }

    public String quoteSnapshotJson(PriceQuote quote) {
        try {
            return objectMapper.writeValueAsString(quote.toMap());
        } catch (Exception ignored) {
            return "{}";
        }
    }

    /** Do not serialize the lazy version association in the settings API. */
    private Map<String, Object> ruleView(ModelPriceRule rule) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", rule.getId());
        view.put("provider", rule.getProvider());
        view.put("media_type", rule.getMediaType());
        view.put("model", rule.getModel());
        view.put("resolution", rule.getResolution());
        view.put("input_mode", rule.getInputMode());
        view.put("rate_unit", rule.getRateUnit());
        view.put("unit_price_cny", rule.getUnitPriceCny());
        view.put("base_price_cny", rule.getBasePriceCny());
        view.put("priority", rule.getPriority());
        view.put("active", rule.getActive());
        view.put("display_name", rule.getDisplayName());
        return view;
    }

    private ModelPriceRule rule(ModelPriceVersion version, String mediaType, String model, String resolution,
                                String inputMode, String rateUnit, String cny, int priority, String displayName) {
        ModelPriceRule rule = new ModelPriceRule();
        rule.setVersion(version);
        rule.setMediaType(mediaType);
        rule.setModel(model);
        rule.setResolution(resolution);
        rule.setInputMode(inputMode);
        rule.setRateUnit(rateUnit);
        rule.setUnitPriceCny(new BigDecimal(cny));
        rule.setBasePriceCny(BigDecimal.ZERO);
        rule.setPriority(priority);
        rule.setDisplayName(displayName);
        return rule;
    }

    private int matchScore(ModelPriceRule rule, String resolution, String inputMode) {
        int score = safeInt(rule.getPriority());
        if (!blank(rule.getResolution()) && normalize(rule.getResolution()).equals(resolution)) score += 10_000;
        if (!blank(rule.getInputMode()) && normalize(rule.getInputMode()).equals(inputMode)) score += 1_000;
        return score;
    }

    private boolean matches(String configured, String actual) {
        return blank(configured) || normalize(configured).equals(actual);
    }

    private String resolveResolution(String mediaType, Map<String, Object> payload) {
        // Canvas submits KIE's resolution tier explicitly. Prefer it over the
        // display-size field so a 3:4 4K image (2448x3264) still quotes as 4K.
        String raw = mediaType.equals("image")
                ? blankOr(text(payload, "resolution"), text(payload, "size"))
                : blankOr(text(payload, "resolution"), text(payload, "mode"));
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("4k") || lower.contains("4096") || lower.contains("3840")) return "4k";
        if (lower.contains("2k") || lower.contains("2048")) return "2k";
        if (lower.contains("1k") || lower.contains("1024")) return "1k";
        if (lower.contains("768")) return "768p";
        if (lower.contains("1080")) return "1080p";
        if (lower.contains("480")) return "480p";
        if (lower.contains("720")) return "720p";
        return normalize(raw);
    }

    private String resolveInputMode(String mediaType, Map<String, Object> payload) {
        if (mediaType.equals("video")) {
            if (hasValues(payload.get("videos")) || hasValues(payload.get("reference_video_urls")) || hasValues(payload.get("video_urls"))) return "video";
            if (bool(payload.get("generate_audio")) || bool(payload.get("sound"))) return "audio";
            if (hasValues(payload.get("audios")) || hasValues(payload.get("reference_audio_urls"))) return "multimodal";
            if (hasValues(payload.get("images")) || hasValues(payload.get("reference_image_urls")) || hasValues(payload.get("image_urls")) || hasValues(payload.get("first_frame_url"))) return "image";
            return "text";
        }
        return hasValues(payload.get("reference_images")) ? "image" : "text";
    }

    private int resolveDuration(String model, Map<String, Object> payload) {
        if (isSeedance25VideoEdit(model, payload)) {
            return resolveSeedance25VideoEditSourceSeconds(payload) * 2;
        }
        boolean videoInput = hasVideoInput(payload);
        if (videoInput) {
            int real = integer(payload.get("video_duration_seconds"), 0);
            if (real > 0) return real;
        }
        return Math.max(1, integer(payload.get("duration"), 1));
    }

    /**
     * KIE locks Seedance 2.5 video editing to the source video's duration. Its
     * billing consumes one per-second leg for source processing and another
     * equally long leg for the generated output.  Keep this intentionally
     * narrow: other models with a video reference still bill their own rules.
     */
    private boolean isSeedance25VideoEdit(String model, Map<String, Object> payload) {
        if (!SEEDANCE_25_MODEL.equals(model) || !hasVideoInput(payload)) return false;
        String mode = normalize(text(payload, "seedance_mode"));
        if ("video_edit".equals(mode)) return true;
        return integer(payload.get("duration"), 0) == -1
                && "adaptive".equals(normalize(text(payload, "aspect_ratio")));
    }

    private boolean hasVideoInput(Map<String, Object> payload) {
        return hasValues(payload.get("reference_video_urls"))
                || hasValues(payload.get("videos")) || hasValues(payload.get("video_urls"));
    }

    private int resolveSeedance25VideoEditSourceSeconds(Map<String, Object> payload) {
        return Math.max(1, integer(payload.get("video_duration_seconds"), 0));
    }

    private BigDecimal priceAmount(ModelPriceRule rule, BigDecimal units, boolean includeBasePrice) {
        BigDecimal base = includeBasePrice ? safeDecimal(rule.getBasePriceCny()) : BigDecimal.ZERO;
        return base.add(safeDecimal(rule.getUnitPriceCny()).multiply(units)).setScale(4, RoundingMode.HALF_UP);
    }

    private String seedance25VideoEditComponentName(ModelPriceRule rule, String leg, int secondsPerRun, int quantity) {
        String multiplier = quantity > 1 ? " × " + quantity + " 项" : "";
        return rule.getDisplayName() + " · 视频编辑" + " · " + leg + " " + secondsPerRun + " 秒" + multiplier;
    }

    private boolean hasValues(Object value) {
        if (value == null) return false;
        if (value instanceof List<?> list) return !list.isEmpty();
        return !String.valueOf(value).isBlank() && !"[]".equals(String.valueOf(value));
    }

    private boolean isMiniMaxH3VideoModel(String model) {
        return MINIMAX_H3_VIDEO_MODELS.contains(model);
    }

    /** Counts unique image URLs across the request shapes used by both canvas entry points. */
    private int miniMaxH3ImageInputCount(Map<String, Object> payload) {
        Set<String> urls = new LinkedHashSet<>();
        for (String key : List.of("images", "reference_image_urls", "image_urls", "first_frame_url", "last_frame_url")) {
            collectImageInputUrls(payload == null ? null : payload.get(key), urls);
        }
        return urls.size();
    }

    private void collectImageInputUrls(Object value, Set<String> urls) {
        if (value == null) return;
        if (value instanceof String text) {
            if (!text.isBlank()) urls.add(text.trim());
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectImageInputUrls(item, urls));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("url", "image_url", "imageUrl", "src")) {
                Object nested = map.get(key);
                if (nested != null) {
                    collectImageInputUrls(nested, urls);
                    return;
                }
            }
        }
    }

    private boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String text(Map<String, Object> values, String key) {
        if (values == null || !values.containsKey(key) || values.get(key) == null) return "";
        return String.valueOf(values.get(key)).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String blankOr(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private BigDecimal decimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value == null ? 0 : value)).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
    }

    private int integer(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record PriceQuote(boolean available,
                             String versionCode,
                             Long ruleId,
                             String displayName,
                             String model,
                             String mediaType,
                             String resolution,
                             String inputMode,
                             int quantity,
                             BigDecimal units,
                             BigDecimal amountCny,
                             List<PriceComponent> components,
                             String message) {

        static PriceQuote available(ModelPriceVersion version, ModelPriceRule rule, String model, String resolution,
                                    String inputMode, int quantity, BigDecimal units, BigDecimal amount,
                                    List<PriceComponent> components, String message) {
            return new PriceQuote(true, version.getVersionCode(), rule.getId(), rule.getDisplayName(), model,
                    rule.getMediaType(), resolution, inputMode, quantity, units, amount,
                    List.copyOf(components),
                    message);
        }

        static PriceQuote unavailable(String message) {
            return unavailable("", "", "", "", message);
        }

        static PriceQuote unavailable(String versionCode, String model, String resolution, String inputMode, String message) {
            return new PriceQuote(false, versionCode, null, "", model, "", resolution, inputMode, 0,
                    BigDecimal.ZERO, null, List.of(), message);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", available);
            result.put("price_version", versionCode);
            result.put("rule_id", ruleId);
            result.put("display_name", displayName);
            result.put("model", model);
            result.put("media_type", mediaType);
            result.put("resolution", resolution);
            result.put("input_mode", inputMode);
            result.put("quantity", quantity);
            result.put("units", units);
            result.put("amount_cny", amountCny);
            result.put("components", components.stream().map(PriceComponent::toMap).toList());
            result.put("credit_to_cny", CREDIT_TO_CNY);
            result.put("message", message);
            return result;
        }
    }

    public record PriceComponent(Long ruleId, String displayName, BigDecimal units, BigDecimal amountCny) {
        static PriceComponent from(ModelPriceRule rule, BigDecimal units, BigDecimal amount) {
            return new PriceComponent(rule.getId(), rule.getDisplayName(), units, amount);
        }

        Map<String, Object> toMap() {
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("rule_id", ruleId);
            component.put("display_name", displayName);
            component.put("units", units);
            component.put("amount_cny", amountCny);
            return component;
        }
    }
}
