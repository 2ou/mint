package com.ai.service.impl;

import com.ai.dto.AplusLayoutTemplateParseRequest;
import com.ai.dto.AplusModuleDefinition;
import com.ai.dto.AplusModuleExtra;
import com.ai.dto.AplusProjectCreateRequest;
import com.ai.dto.AplusTemplateResponse;
import com.ai.dto.AplusTemplateSaveRequest;
import com.ai.entity.AplusTemplate;
import com.ai.repository.AplusTemplateRepository;
import com.ai.service.AplusTemplateService;
import com.ai.service.TextModelService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AplusTemplateServiceImpl implements AplusTemplateService {

    private record LayoutBlueprintResult(String blueprintJson, String qualityReportJson) {}

    public static final String TYPE_FORM_TEMPLATE = "FORM_TEMPLATE";
    public static final String TYPE_LAYOUT_REFERENCE = "LAYOUT_REFERENCE";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private final AplusTemplateRepository templateRepository;
    private final TextModelService textModelService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AplusTemplateResponse saveTemplate(AplusTemplateSaveRequest request, String operator, String shopName) {
        validateTemplateName(request.getTemplateName());

        AplusTemplate template = new AplusTemplate();
        template.setTemplateName(request.getTemplateName().trim());
        template.setTemplateType(normalizeTemplateType(request.getTemplateType()));
        template.setTemplateStatus(normalizeTemplateStatus(request.getTemplateStatus()));
        template.setSpu(blankToNull(request.getSpu()));
        template.setReferenceImageUrl(blankToNull(request.getReferenceImageUrl()));
        template.setSellingPoints(request.getSellingPoints());
        template.setLayoutReferenceImageUrl(blankToNull(request.getLayoutReferenceImageUrl()));
        template.setLayoutBlueprintJson(normalizeBlueprintOrNull(request.getLayoutBlueprintJson()));
        template.setLayoutQualityReportJson(normalizeBlueprintOrNull(request.getLayoutQualityReportJson()));
        template.setOperator(operator);
        template.setShopName(shopName);

        template.setSelectedModules(toJson(normalizeSelectedModules(request.getSelectedModules()), "[]"));
        template.setModuleExtras(toJson(request.getModuleExtras() != null ? request.getModuleExtras() : Map.of(), "{}"));

        template = templateRepository.save(template);
        log.info("[A+] template saved: id={}, name={}, type={}",
                template.getId(), template.getTemplateName(), template.getTemplateType());
        return AplusTemplateResponse.from(template);
    }

    @Override
    @Transactional
    public AplusTemplateResponse parseLayoutTemplate(AplusLayoutTemplateParseRequest request,
                                                     String operator,
                                                     String shopName) {
        validateTemplateName(request.getTemplateName());
        if (request.getLayoutReferenceImageUrl() == null || request.getLayoutReferenceImageUrl().isBlank()) {
            throw new RuntimeException("layoutReferenceImageUrl is required");
        }

        List<String> modules = normalizeSelectedModules(request.getSelectedModules());
        String layoutReferenceImageUrl = request.getLayoutReferenceImageUrl().trim();
        LayoutBlueprintResult blueprint = generateLayoutBlueprint(request, modules, layoutReferenceImageUrl);

        AplusTemplateSaveRequest saveRequest = new AplusTemplateSaveRequest();
        saveRequest.setTemplateName(request.getTemplateName().trim());
        saveRequest.setTemplateType(TYPE_LAYOUT_REFERENCE);
        saveRequest.setTemplateStatus(normalizeTemplateStatus(request.getTemplateStatus()));
        saveRequest.setSelectedModules(modules);
        saveRequest.setLayoutReferenceImageUrl(layoutReferenceImageUrl);
        saveRequest.setLayoutBlueprintJson(blueprint.blueprintJson());
        saveRequest.setLayoutQualityReportJson(blueprint.qualityReportJson());
        saveRequest.setModuleExtras(buildLayoutModuleExtras(blueprint.blueprintJson(), layoutReferenceImageUrl, modules));
        return saveTemplate(saveRequest, operator, shopName);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AplusTemplateResponse> getTemplatePage(int page, int size, String templateName, String spu) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AplusTemplate> result;
        if (templateName != null && !templateName.isBlank() && spu != null && !spu.isBlank()) {
            result = templateRepository.findByTemplateNameContainingAndSpu(templateName, spu, pageable);
        } else if (templateName != null && !templateName.isBlank()) {
            result = templateRepository.findByTemplateNameContaining(templateName, pageable);
        } else if (spu != null && !spu.isBlank()) {
            result = templateRepository.findBySpu(spu, pageable);
        } else {
            result = templateRepository.findAll(pageable);
        }
        return result.map(AplusTemplateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public AplusTemplateResponse getTemplateById(Long id) {
        AplusTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ template not found: " + id));
        return AplusTemplateResponse.from(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        AplusTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ template not found: " + id));
        templateRepository.delete(template);
        log.info("[A+] template deleted: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public AplusProjectCreateRequest applyTemplate(Long id) {
        AplusTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ template not found: " + id));

        AplusProjectCreateRequest request = new AplusProjectCreateRequest();
        request.setProjectName(template.getTemplateName());

        List<String> modules = parseSelectedModules(template.getSelectedModules());
        request.setSelectedModules(modules);

        if (TYPE_LAYOUT_REFERENCE.equals(normalizeTemplateType(template.getTemplateType()))) {
            request.setLayoutTemplateId(template.getId());
            request.setLayoutTemplateName(template.getTemplateName());
            request.setLayoutReferenceImageUrl(template.getLayoutReferenceImageUrl());
            request.setLayoutBlueprintJson(template.getLayoutBlueprintJson());
            request.setModuleExtras(buildLayoutModuleExtras(
                    template.getLayoutBlueprintJson(),
                    template.getLayoutReferenceImageUrl(),
                    modules));
            return request;
        }

        request.setSpu(template.getSpu());
        request.setReferenceImageUrl(template.getReferenceImageUrl());
        request.setSellingPoints(template.getSellingPoints());
        request.setModuleExtras(parseModuleExtras(template.getModuleExtras()));
        return request;
    }

    private LayoutBlueprintResult generateLayoutBlueprint(AplusLayoutTemplateParseRequest request,
                                                          List<String> modules,
                                                          String layoutReferenceImageUrl) {
        try {
            String raw = textModelService.generateRawPromptWithImages(
                    buildLayoutParseSystemPrompt(),
                    buildLayoutParseUserPrompt(request, modules, layoutReferenceImageUrl),
                    List.of(layoutReferenceImageUrl),
                    normalizeTextModel(request.getTextModel()));
            String blueprint = normalizeBlueprintJson(raw, modules, layoutReferenceImageUrl, "MODEL_PARSED");
            return new LayoutBlueprintResult(blueprint, buildLayoutQualityReport(blueprint, modules));
        } catch (Exception e) {
            log.warn("[A+] layout template parse failed, using fallback blueprint: {}", e.getMessage());
            String blueprint = fallbackLayoutBlueprint(modules, layoutReferenceImageUrl, request.getNotes(), "FALLBACK");
            return new LayoutBlueprintResult(blueprint, buildLayoutQualityReport(blueprint, modules));
        }
    }

    private String buildLayoutParseSystemPrompt() {
        return """
                You are an A+ Content layout architect for women's fashion e-commerce.
                Convert an A+ layout reference into a reusable structure blueprint.
                The attached image is the layout reference and must be visually inspected before writing the blueprint.
                The reference is for structure only. Never preserve product identity, model gender, faces, brand logos, colors, exact scenes, or exact photos.
                Return JSON only. No markdown, no explanation.
                All shopper-facing text instructions must be English only.
                """;
    }

    private String buildLayoutParseUserPrompt(AplusLayoutTemplateParseRequest request,
                                              List<String> modules,
                                              String layoutReferenceImageUrl) {
        return """
                Parse this A+ layout reference into a reusable JSON blueprint.

                Layout reference image URL: %s
                User notes: %s
                Modules to support: %s

                Required JSON shape:
                {
                  "version": "1.0",
                  "templateType": "LAYOUT_REFERENCE",
                  "parseMode": "MODEL_PARSED",
                  "sourceImageUrl": "...",
                  "globalStyle": {
                    "pageRhythm": "...",
                    "panelStyle": "...",
                    "imageCropStyle": "...",
                    "typography": "...",
                    "spacing": "...",
                    "colorGuidance": "neutral, product-adaptive; do not copy reference product colors"
                  },
                  "designTokens": {
                    "gridColumns": "...",
                    "outerMargin": "percentage or relative rule",
                    "cardRadius": "...",
                    "panelGap": "...",
                    "textSafeZones": ["..."],
                    "visualDensity": "...",
                    "modelProfile": "..."
                  },
                  "modules": {
                    "AD-01": {
                      "role": "hero",
                      "layout": "...",
                      "imageZones": ["..."],
                      "textZones": ["..."],
                      "textRules": ["English only", "short headline and subline"],
                      "doNotCopy": ["reference product", "model identity", "brand logo"]
                    }
                  }
                }

                Create one module blueprint per requested AD code. Each module must describe one standalone 21:9 web image, not one long infographic page.
                """.formatted(
                layoutReferenceImageUrl,
                request.getNotes() == null ? "" : request.getNotes(),
                String.join(", ", modules));
    }

    private String normalizeBlueprintJson(String raw,
                                          List<String> modules,
                                          String layoutReferenceImageUrl,
                                          String parseMode) {
        String json = extractJson(raw);
        if (json == null) {
            return fallbackLayoutBlueprint(modules, layoutReferenceImageUrl, null, "FALLBACK");
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (!parsed.isObject() || !parsed.has("modules") || !parsed.get("modules").isObject()) {
                return fallbackLayoutBlueprint(modules, layoutReferenceImageUrl, null, "FALLBACK");
            }
            ObjectNode root = (ObjectNode) parsed.deepCopy();
            root.put("version", root.path("version").asText("1.0"));
            root.put("templateType", TYPE_LAYOUT_REFERENCE);
            root.put("parseMode", parseMode);
            root.put("sourceImageUrl", layoutReferenceImageUrl);
            root.put("usageRule", "Use this blueprint for layout structure only. The project product truth image is the garment source of truth and cannot be overridden by this layout reference.");
            normalizeBlueprintShape(root, modules, layoutReferenceImageUrl);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return fallbackLayoutBlueprint(modules, layoutReferenceImageUrl, null, "FALLBACK");
        }
    }

    private String fallbackLayoutBlueprint(List<String> modules,
                                           String layoutReferenceImageUrl,
                                           String notes,
                                           String parseMode) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", "1.0");
            root.put("templateType", TYPE_LAYOUT_REFERENCE);
            root.put("parseMode", parseMode);
            root.put("sourceImageUrl", layoutReferenceImageUrl);
            root.put("usageRule", "Use the reference image and this blueprint for layout structure only. The project product truth image remains the garment source of truth.");
            if (notes != null && !notes.isBlank()) {
                root.put("notes", notes.trim());
            }

            ObjectNode globalStyle = root.putObject("globalStyle");
            globalStyle.put("pageRhythm", "Premium A+ page rhythm split into independent 21:9 web modules.");
            globalStyle.put("panelStyle", "Clean white or soft neutral panels, rounded cards, subtle dividers, balanced spacing.");
            globalStyle.put("imageCropStyle", "Commercial product and lifestyle crops with clear hierarchy and safe margins.");
            globalStyle.put("typography", "Short readable English-only headings, labels, and captions.");
            globalStyle.put("spacing", "Dense but controlled information layout; no empty technical sections.");
            globalStyle.put("colorGuidance", "Product-adaptive neutral system; do not copy reference product colors.");

            ObjectNode designTokens = root.putObject("designTokens");
            designTokens.put("gridColumns", "Use a simple 12-column horizontal web grid.");
            designTokens.put("outerMargin", "Keep 5-8% outer safe margins.");
            designTokens.put("cardRadius", "Soft premium rounded cards; consistent radius.");
            designTokens.put("panelGap", "Use compact, even panel gaps and aligned baselines.");
            ArrayNode safeZones = designTokens.putArray("textSafeZones");
            safeZones.add("Reserve high-contrast text-safe areas away from the product silhouette.");
            designTokens.put("visualDensity", "Dense but breathable e-commerce information hierarchy.");
            designTokens.put("modelProfile", "Commercial American catalog woman, natural and consistent across model modules.");

            ObjectNode moduleNode = root.putObject("modules");
            for (String code : modules) {
                moduleNode.set(code, fallbackModuleBlueprint(code));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build layout blueprint", e);
        }
    }

    private ObjectNode fallbackModuleBlueprint(String code) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode imageZones = node.putArray("imageZones");
        ArrayNode textZones = node.putArray("textZones");
        ArrayNode textRules = node.putArray("textRules");
        ArrayNode doNotCopy = node.putArray("doNotCopy");

        textRules.add("English only");
        textRules.add("Short, high-contrast, readable shopper-facing text");
        doNotCopy.add("reference product");
        doNotCopy.add("reference model identity");
        doNotCopy.add("reference brand logo");
        doNotCopy.add("reference product color");
        doNotCopy.add("exact photos or exact scene identity");

        switch (code) {
            case "AD-01" -> {
                node.put("role", "hero");
                node.put("layout", "Hero-style banner with strong product/model anchor and headline/subline text zone.");
                imageZones.add("Large main visual zone with clear product focus.");
                textZones.add("Headline and short subline in a clean safe area.");
            }
            case "AD-02" -> {
                node.put("role", "fabric_story");
                node.put("layout", "Split material story with product view, macro fabric/detail crop, and compact labels.");
                imageZones.add("Product cutout or folded garment zone.");
                imageZones.add("Macro texture/detail zone.");
                textZones.add("2-4 fabric or feel labels.");
            }
            case "AD-03" -> {
                node.put("role", "design_details");
                node.put("layout", "Product-centered detail grid with inset close-ups and connector lines.");
                imageZones.add("Main product zone.");
                imageZones.add("3-4 close-up detail cards.");
                textZones.add("Short detail labels tied to real visible details.");
            }
            case "AD-04" -> {
                node.put("role", "scenario_cards");
                node.put("layout", "Three scenario cards with consistent garment identity and one caption per card.");
                imageZones.add("Three equal lifestyle or styling panels.");
                textZones.add("One short English caption per scenario.");
            }
            case "AD-05" -> {
                node.put("role", "comfort_wearability");
                node.put("layout", "Large comfort proof image plus one fabric/fit detail inset and benefit labels.");
                imageZones.add("Primary model-worn or comfort proof zone.");
                imageZones.add("Small fabric or fit detail inset.");
                textZones.add("2-4 comfort and fit labels.");
            }
            case "AD-06" -> {
                node.put("role", "fit_guide");
                node.put("layout", "Technical fit guide with product view, measurement arrows, and compact chart or fit guidance.");
                imageZones.add("Front product or fit reference zone.");
                imageZones.add("Measurement arrow or chart zone.");
                textZones.add("Size chart only when exact measurements are provided; otherwise non-numeric fit guidance.");
            }
            case "AD-07" -> {
                node.put("role", "care_closing");
                node.put("layout", "Care, styling, or brand closing module with still-life product and short bullet text.");
                imageZones.add("Folded or neat product still-life zone.");
                textZones.add("Short care/quality bullet text.");
            }
            default -> {
                node.put("role", "generic_module");
                node.put("layout", "Clean product-focused A+ module with readable benefit text.");
                imageZones.add("Primary product visual zone.");
                textZones.add("Short headline and benefit labels.");
            }
        }
        return node;
    }

    private void normalizeBlueprintShape(ObjectNode root, List<String> modules, String layoutReferenceImageUrl) {
        JsonNode fallbackNode = readJsonOrNull(fallbackLayoutBlueprint(modules, layoutReferenceImageUrl, null, "FALLBACK"));
        ObjectNode fallback = fallbackNode instanceof ObjectNode ? (ObjectNode) fallbackNode : objectMapper.createObjectNode();
        if (!(root.get("globalStyle") instanceof ObjectNode)) {
            root.set("globalStyle", fallback.path("globalStyle").deepCopy());
        }
        if (!(root.get("designTokens") instanceof ObjectNode)) {
            root.set("designTokens", fallback.path("designTokens").deepCopy());
        }
        ObjectNode moduleRoot = root.get("modules") instanceof ObjectNode item ? item : root.putObject("modules");
        ObjectNode fallbackModules = fallback.path("modules") instanceof ObjectNode item ? item : objectMapper.createObjectNode();
        for (String code : modules) {
            ObjectNode module = moduleRoot.get(code) instanceof ObjectNode item ? item : moduleRoot.putObject(code);
            ObjectNode fallbackModule = fallbackModules.get(code) instanceof ObjectNode item
                    ? item : fallbackModuleBlueprint(code);
            fillText(module, fallbackModule, "role");
            fillText(module, fallbackModule, "layout");
            fillArray(module, fallbackModule, "imageZones");
            fillArray(module, fallbackModule, "textZones");
            fillArray(module, fallbackModule, "textRules");
            fillArray(module, fallbackModule, "doNotCopy");
        }
    }

    private void fillText(ObjectNode target, ObjectNode fallback, String key) {
        if (target.path(key).asText("").isBlank()) target.put(key, fallback.path(key).asText(""));
    }

    private void fillArray(ObjectNode target, ObjectNode fallback, String key) {
        if (!target.path(key).isArray() || target.path(key).isEmpty()) {
            target.set(key, fallback.path(key).deepCopy());
        }
    }

    private String buildLayoutQualityReport(String blueprintJson, List<String> modules) {
        ObjectNode report = objectMapper.createObjectNode();
        ArrayNode warnings = report.putArray("warnings");
        JsonNode root = readJsonOrNull(blueprintJson);
        String parseMode = root == null ? "FALLBACK" : root.path("parseMode").asText("FALLBACK");
        report.put("parseMode", parseMode);
        report.put("referenceVisuallyInspected", "MODEL_PARSED".equals(parseMode));
        report.put("targetAspectRatio", "21:9");
        ArrayNode supportedModules = report.putArray("supportedModules");
        JsonNode moduleRoot = root != null ? root.path("modules") : null;
        for (String code : modules) {
            if (moduleRoot != null && moduleRoot.path(code).isObject()) supportedModules.add(code);
            else warnings.add("Missing normalized blueprint for " + code);
        }
        boolean hasTokens = root != null && root.path("designTokens").isObject();
        report.put("hasDesignTokens", hasTokens);
        if (!hasTokens) warnings.add("Design tokens were missing and fallback tokens were inserted.");
        if (!"MODEL_PARSED".equals(parseMode)) warnings.add("The visual parser was unavailable; a safe fallback layout is in use.");
        report.put("confidence", "MODEL_PARSED".equals(parseMode) ? 0.86 : 0.48);
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            return "{\"parseMode\":\"FALLBACK\",\"confidence\":0.0}";
        }
    }

    public Map<String, AplusModuleExtra> buildLayoutModuleExtras(String blueprintJson,
                                                                  String layoutReferenceImageUrl,
                                                                  List<String> modules) {
        Map<String, AplusModuleExtra> result = new LinkedHashMap<>();
        JsonNode root = readJsonOrNull(blueprintJson);
        JsonNode globalStyle = root != null ? root.path("globalStyle") : null;
        JsonNode moduleRoot = root != null ? root.path("modules") : null;

        for (String code : normalizeSelectedModules(modules)) {
            AplusModuleExtra extra = new AplusModuleExtra();
            extra.setSupplementaryText(buildLayoutInstructionText(code, globalStyle, moduleRoot, layoutReferenceImageUrl));
            result.put(code, extra);
        }
        return result;
    }

    private String buildLayoutInstructionText(String code,
                                              JsonNode globalStyle,
                                              JsonNode moduleRoot,
                                              String layoutReferenceImageUrl) {
        JsonNode moduleBlueprint = moduleRoot != null ? moduleRoot.path(code) : null;
        String global = globalStyle != null && !globalStyle.isMissingNode() ? globalStyle.toString() : "{}";
        String module = moduleBlueprint != null && !moduleBlueprint.isMissingNode() ? moduleBlueprint.toString() : "{}";
        return """
                Structure template mode:
                - Use the selected A+ layout template for layout structure only.
                - Generate only this AD module as one standalone 21:9 web image; do not generate a full long infographic page.
                - The product truth image is the garment source of truth and must remain unchanged in category, silhouette, color, print, fabric, neckline, sleeves, hem, trims, and visible construction.
                - The layout reference image is only for hierarchy, panel rhythm, rounded card style, image crop rhythm, text placement, and information density.
                - Do not copy the reference product, model gender, faces, poses, product colors, brand logo, exact scene, accessories, or exact photos.
                - All visible text inside the final image must be English only; no Chinese characters or bilingual captions.
                Layout reference image URL: %s
                Global layout style JSON: %s
                Module %s blueprint JSON: %s
                """.formatted(
                layoutReferenceImageUrl == null ? "" : layoutReferenceImageUrl,
                global,
                code,
                module);
    }

    private List<String> parseSelectedModules(String selectedModulesJson) {
        try {
            if (selectedModulesJson != null && !selectedModulesJson.isBlank()) {
                return normalizeSelectedModules(objectMapper.readValue(selectedModulesJson, new TypeReference<List<String>>() {}));
            }
        } catch (JsonProcessingException e) {
            log.warn("[A+] selectedModules parse failed");
        }
        return new ArrayList<>(AplusModuleDefinition.MODULES.keySet());
    }

    private Map<String, AplusModuleExtra> parseModuleExtras(String moduleExtras) {
        try {
            if (moduleExtras != null && !moduleExtras.isBlank() && !"{}".equals(moduleExtras)) {
                return objectMapper.readValue(moduleExtras, new TypeReference<Map<String, AplusModuleExtra>>() {});
            }
        } catch (JsonProcessingException e) {
            log.warn("[A+] moduleExtras parse failed");
        }
        return Map.of();
    }

    private List<String> normalizeSelectedModules(List<String> selectedModules) {
        List<String> source = selectedModules == null || selectedModules.isEmpty()
                ? new ArrayList<>(AplusModuleDefinition.MODULES.keySet())
                : selectedModules;
        return source.stream()
                .filter(code -> code != null && AplusModuleDefinition.MODULES.containsKey(code))
                .distinct()
                .toList();
    }

    private String normalizeTemplateType(String templateType) {
        if (templateType == null || templateType.isBlank()) {
            return TYPE_FORM_TEMPLATE;
        }
        String normalized = templateType.trim().toUpperCase();
        if (TYPE_LAYOUT_REFERENCE.equals(normalized)) {
            return TYPE_LAYOUT_REFERENCE;
        }
        return TYPE_FORM_TEMPLATE;
    }

    private String normalizeTemplateStatus(String templateStatus) {
        if (templateStatus == null || templateStatus.isBlank()) {
            return STATUS_ACTIVE;
        }
        String normalized = templateStatus.trim().toUpperCase();
        if ("DRAFT".equals(normalized) || STATUS_ACTIVE.equals(normalized) || "DISABLED".equals(normalized)) {
            return normalized;
        }
        return STATUS_ACTIVE;
    }

    private String normalizeTextModel(String textModel) {
        return KieGptModels.normalizeTextModel(textModel);
    }

    private String normalizeBlueprintOrNull(String blueprintJson) {
        if (blueprintJson == null || blueprintJson.isBlank()) {
            return null;
        }
        JsonNode node = readJsonOrNull(blueprintJson);
        if (node == null) {
            return blueprintJson;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return blueprintJson;
        }
    }

    private JsonNode readJsonOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private void validateTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new RuntimeException("templateName is required");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
