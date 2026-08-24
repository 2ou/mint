package com.ai.service.impl;

import com.ai.dto.AplusReferenceImage;
import com.ai.entity.AplusImageTask;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.service.AplusQualityService;
import com.ai.service.TextModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Separates generation success from release confidence. A flagged image is still usable,
 * but the operator can see why it should be reviewed before it is published.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AplusQualityServiceImpl implements AplusQualityService {

    private final AplusImageTaskRepository imageTaskRepository;
    private final TextModelService textModelService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void evaluate(Long taskId) {
        AplusImageTask task = imageTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        String resultUrl = firstNonBlank(task.getResultTempUrl(), task.getResultOssUrl());
        String productTruth = productTruthUrl(task);
        if (resultUrl.isBlank() || productTruth.isBlank()) {
            task.setQualityStatus("NOT_EVALUATED");
            task.setQualityReportJson(report("SKIPPED", 0,
                    "A product truth image and a generated result URL are both required for multimodal QA."));
            imageTaskRepository.save(task);
            return;
        }

        task.setQualityStatus("EVALUATING");
        imageTaskRepository.save(task);
        try {
            String systemPrompt = "You are a strict e-commerce image QA director. Compare image input [0] PRODUCT TRUTH with "
                    + "image input [1] GENERATED A+ MODULE. Return JSON only, with no markdown. "
                    + "Schema: {\"overallScore\":0-100,\"garmentFidelityScore\":0-100,\"layoutScore\":0-100,"
                    + "\"textRisk\":\"NONE|LOW|HIGH\",\"watermarkOrLogoRisk\":\"NONE|LOW|HIGH\","
                    + "\"issues\":[\"short English issue\"],\"recommendation\":\"short English action\"}. "
                    + "Check garment type, silhouette, color, print, neckline, sleeves, hem, fabric appearance, duplicated limbs, "
                    + "unreadable or non-English text, watermarks, accidental brand logos, and unsafe crop. Do not punish intentional scene or layout changes.";
            String userPrompt = "Module " + task.getModuleCode()
                    + ". Input [0] is product truth; input [1] is generated output. Evaluate publish readiness.";
            String raw = textModelService.generateRawPromptWithImages(systemPrompt, userPrompt,
                    List.of(productTruth, resultUrl), KieGptModels.DEFAULT_TEXT_MODEL);
            String normalized = normalizeReport(raw);
            JsonNode report = objectMapper.readTree(normalized);
            int overall = report.path("overallScore").asInt(0);
            int fidelity = report.path("garmentFidelityScore").asInt(0);
            boolean highRisk = "HIGH".equalsIgnoreCase(report.path("textRisk").asText())
                    || "HIGH".equalsIgnoreCase(report.path("watermarkOrLogoRisk").asText());
            task.setQualityReportJson(normalized);
            task.setQualityStatus(overall >= 80 && fidelity >= 80 && !highRisk ? "PASSED" : "FLAGGED");
            imageTaskRepository.save(task);
        } catch (Exception e) {
            log.warn("[A+] quality evaluation failed: taskId={}, error={}", taskId, e.getMessage());
            task.setQualityStatus("NOT_EVALUATED");
            task.setQualityReportJson(report("UNAVAILABLE", 0, "Automated QA could not complete: " + safeMessage(e)));
            imageTaskRepository.save(task);
        }
    }

    private String productTruthUrl(AplusImageTask task) {
        try {
            List<AplusReferenceImage> references = objectMapper.readValue(
                    task.getReferenceImagesJson() == null ? "[]" : task.getReferenceImagesJson(),
                    new TypeReference<List<AplusReferenceImage>>() {});
            return references.stream()
                    .filter(ref -> ref != null && AplusReferenceImage.PRODUCT_TRUTH.equalsIgnoreCase(ref.getRole()))
                    .map(AplusReferenceImage::getUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst()
                    .orElse(firstNonBlank(task.getProject().getReferenceImageUrl()));
        } catch (Exception ignored) {
            return firstNonBlank(task.getProject().getReferenceImageUrl());
        }
    }

    private String normalizeReport(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty QA response");
        }
        String cleaned = raw.trim().replaceFirst("(?is)^```(?:json)?\\s*", "").replaceFirst("(?is)\\s*```$", "");
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("QA response is not JSON");
        }
        JsonNode root = objectMapper.readTree(cleaned.substring(start, end + 1));
        if (!root.isObject()) {
            throw new IllegalArgumentException("QA JSON must be an object");
        }
        return objectMapper.writeValueAsString(root);
    }

    private String report(String state, int score, String recommendation) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("state", state);
            value.put("overallScore", score);
            value.put("issues", new ArrayList<>());
            value.put("recommendation", recommendation);
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
