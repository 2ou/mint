package com.ai.service.impl;

import com.ai.dto.AplusModuleDefinition;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusProjectStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.service.AplusCopyService;
import com.ai.service.TextModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AplusCopyServiceImpl implements AplusCopyService {

    private static final Pattern MODULE_PATTERN =
            Pattern.compile("(?im)^#{1,3}\\s*(AD-\\d{2})\\b[\\s\\S]*?(?=^#{1,3}\\s*AD-\\d{2}\\b|\\z)");

    private final TextModelService textModelService;
    private final AplusProjectRepository projectRepository;
    private final AplusImageTaskRepository imageTaskRepository;
    private final ObjectMapper objectMapper;

    @Value("${aplus.skill.path:}")
    private String skillTemplatePath;

    @Override
    @Transactional
    public void generateCopy(Long projectId) {
        generateCopy(projectId, KieGptModels.DEFAULT_TEXT_MODEL);
    }

    @Override
    @Transactional
    public void generateCopy(Long projectId, String textModel) {
        String normalizedTextModel = KieGptModels.normalizeTextModel(textModel);
        AplusProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + projectId));

        project.setStatus(AplusProjectStatus.GENERATING_COPY.name());
        project.setErrorMessage(null);
        projectRepository.save(project);

        try {
            String systemPrompt = buildSystemPrompt(readSkillTemplate(), project.getSelectedModules(), hasAplusReference(project));
            String userPrompt = buildUserPrompt(project);

            log.info("[A+] generating copy: projectId={}, textModel={}", projectId, normalizedTextModel);
            String rawCopy = textModelService.generateRawPrompt(systemPrompt, userPrompt, normalizedTextModel);
            CopyParseResult parseResult = parseModuleCopy(rawCopy);
            Map<String, String> moduleContents = parseResult.moduleContents();
            if (moduleContents.isEmpty()) {
                throw new RuntimeException("A+ 文案结构解析失败，文本模型未返回 AD-XX 模块结构");
            }

            project.setAplusMarkdown(parseResult.displayMarkdown());
            project.setStatus(AplusProjectStatus.COPY_DONE.name());
            projectRepository.save(project);

            List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(projectId);
            for (AplusImageTask task : tasks) {
                String content = moduleContents.get(task.getModuleCode());
                if (content == null || content.isBlank()) {
                    content = buildFallbackModuleCopy(task, project);
                }
                task.setModuleCopy(content);
                imageTaskRepository.save(task);
            }

            log.info("[A+] copy done: projectId={}, parsedModules={}", projectId, moduleContents.size());
        } catch (Exception e) {
            log.error("[A+] copy generation failed: projectId={}, error={}", projectId, e.getMessage(), e);
            project.setStatus(AplusProjectStatus.FAILED.name());
            project.setErrorMessage("A+ 文案生成失败: " + e.getMessage());
            projectRepository.save(project);
            throw new RuntimeException("A+ 文案生成失败: " + e.getMessage(), e);
        }
    }



    private String readSkillTemplate() {
        if (skillTemplatePath != null && !skillTemplatePath.isBlank()) {
            try {
                Path path = Path.of(skillTemplatePath);
                if (Files.exists(path)) {
                    return Files.readString(path);
                }
                log.warn("[A+] configured skill template not found: {}", skillTemplatePath);
            } catch (IOException e) {
                log.warn("[A+] read configured skill template failed: {}", e.getMessage());
            }
        }

        try {
            ClassPathResource resource = new ClassPathResource("skills/aplus-content-skill.md");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.warn("[A+] read classpath skill template failed: {}", e.getMessage());
        }
        return "";
    }

    private String buildSystemPrompt(String skillTemplate, String selectedModulesJson, boolean hasAplusReference) {
        Set<String> selectedCodes = readSelectedModules(selectedModulesJson);
        StringBuilder sb = new StringBuilder();
        sb.append("# Role\n");
        sb.append("You are a senior Amazon/Walmart A+ Content creative director for women's fashion detail pages. ");
        sb.append("You write production-ready module briefs for image-to-image AI generation. ");
        sb.append("Your priority is commercial usability: product fidelity, clear buyer value, premium layout, and consistent visual system.\n\n");
        if (skillTemplate != null && !skillTemplate.isBlank()) {
            sb.append("# Optional Skill Reference\n");
            sb.append("Use the following reference only when it improves the A+ module plan. Do not copy implementation notes or file paths.\n");
            sb.append(skillTemplate).append("\n\n");
        }
        sb.append("# Selected A+ Modules\n");
        for (String code : selectedCodes) {
            String name = AplusModuleDefinition.MODULES.get(code);
            if (name == null) {
                continue;
            }
            sb.append("## ").append(code).append(" ").append(name).append("\n");
            sb.append("- Visual goal: ").append(AplusModuleDefinition.VISUAL_POSITIONS.getOrDefault(code, "")).append("\n");
            sb.append("- Required user inputs: ").append(AplusModuleDefinition.EXTRA_HINTS.getOrDefault(code, "")).append("\n\n");
        }
        sb.append("# Non-Negotiable Visual Rules\n");
        if (hasAplusReference) {
            sb.append("- The uploaded A+ reference image is for A+ layout and visual system only: hierarchy, panel rhythm, crop style, text placement, information density, and overall presentation quality.\n");
            sb.append("- Do not use the A+ reference image as the garment or product source of truth.\n");
            sb.append("- Do not copy the A+ reference product, model identity, face, pose, gender, scene, accessories, colors, brand, logo, or exact photos.\n");
            sb.append("- Garment identity must come from SPU, selling points, module-specific user text, and supplementary product images when provided.\n");
            sb.append("- For AD-01, AD-04, and AD-05, plan realistic American model and lifestyle scenes when useful. The model must wear the garment described by the user's product information.\n");
        } else {
            sb.append("- No A+ reference image is provided. Build the A+ layout from SPU, selling points, module-specific user text, and supplementary images when provided.\n");
            sb.append("- Keep the garment design conservative, commercially plausible, and aligned with the supplied product information.\n");
            sb.append("- Do not invent logos, brand labels, new prints, decorative hardware, or unsupported garment categories. If details are missing, use simple e-commerce-safe construction details.\n");
            sb.append("- For AD-01, AD-04, and AD-05, plan realistic American model and lifestyle scenes when useful. The model must wear the garment described by the product information.\n");
        }
        sb.append("- For AD-02, AD-03, AD-06, and AD-07, prefer product/detail/technical layouts unless the user provided module-specific model or scene instructions.\n");
        sb.append("- Model guidance: use Curve / Plus-Size or Commercial / Catalog American women for plus-size, flowy, V-neck, tunic, babydoll, or coverage-focused tops; age 30-42, natural skin texture, body-positive confidence.\n");
        sb.append("- Scene guidance: use authentic American lifestyle scenes such as modern apartment, suburban home, coffee shop, casual office, NYC/LA street, garden/patio, or weekend errands. Avoid Chinese architecture, Chinese furniture, influencer poses, porcelain skin, and over-retouched styling.\n");
        sb.append("- Keep all 7 modules in one consistent A+ visual system: same lighting family, color palette, border radius, spacing rhythm, product scale logic, and brand tone.\n");
        sb.append("- Each module must have one clear buyer-facing purpose. Avoid generic lifestyle filler.\n");
        sb.append("- Every generated A+ module must include short readable English text that explains the buyer benefit for that page.\n");
        sb.append("- All image-rendered text must be English only. Do not output Chinese characters, Chinese labels, bilingual captions, or untranslated Chinese user text in Copy Text.\n");
        sb.append("- AD-01 needs a headline and short subline. AD-02 needs fabric/feel labels. AD-03 needs design-detail labels. AD-04 needs scenario captions. AD-05 needs comfort/fit labels.\n");
        sb.append("- AD-06 must include a compact size chart when size data is provided in the product information or AD-06 module instructions. Reproduce supplied size numbers exactly, usually with columns like Size, Bust, Length, and Sleeve.\n");
        sb.append("- If AD-06 has no supplied measurements, do not invent numeric measurements; use size labels and fit guidance instead.\n");
        sb.append("- AD-07 must include care/quality explanation text, such as washing guidance, fabric care, and everyday value points.\n");
        sb.append("- Keep image text brief, high-contrast, and commercially useful; avoid long paragraphs.\n");
        sb.append("- Favor realistic product material, clean commercial lighting, editorial e-commerce layout, and high-end apparel detail-page composition.\n\n");
        sb.append("# Shared Style Anchor\n");
        sb.append(AplusModuleDefinition.STYLE_ANCHOR).append("\n\n");
        sb.append("# Output Contract\n");
        sb.append("Return JSON only. No markdown, no code fence, no explanation.\n");
        sb.append("Use this exact schema:\n");
        sb.append("{\"modules\":[{\"code\":\"AD-01\",\"moduleName\":\"Brand Hero\",\"coreSellingPoint\":\"one concise English buyer benefit\",\"visualDescription\":\"detailed English image-generation direction covering layout, product placement, background, lighting, composition, source-product fidelity, and readable text placement\",\"copyText\":[\"short headline\",\"short label 1\",\"short label 2\"],\"qualityGuards\":\"one English line listing what must not change from the source garment or product information\"}]}\n");
        sb.append("Output exactly one object for each selected module, in selected-module order.\n");
        sb.append("All string values must be English only and must not contain Chinese characters.\n");
        return sb.toString();
    }

    private String buildUserPrompt(AplusProject project) {
        StringBuilder sb = new StringBuilder();
        boolean hasAplusReference = hasAplusReference(project);
        sb.append("# Product Information\n\n");
        sb.append("SPU: ").append(project.getSpu()).append("\n");
        if (hasAplusReference) {
            sb.append("A+ Reference Image: ").append(project.getReferenceImageUrl()).append("\n");
            sb.append("A+ Reference Rule: Use this image only for layout, hierarchy, crop rhythm, text placement, information density, and style quality. Do not copy its product, model, brand, logo, face, exact scene, or product colors.\n\n");
        } else {
            sb.append("A+ Reference Image: Not provided.\n\n");
        }
        sb.append("Raw Selling Points:\n").append(project.getSellingPoints()).append("\n\n");

        List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(project.getId());
        if (!tasks.isEmpty()) {
            sb.append("# Required Module Order\n");
            for (AplusImageTask task : tasks) {
                sb.append("- ").append(task.getModuleCode()).append(" ").append(task.getModuleName()).append("\n");
            }
            sb.append("\n");
        }

        boolean hasExtras = false;
        for (AplusImageTask task : tasks) {
            boolean hasSupplementaryText = task.getSupplementaryText() != null && !task.getSupplementaryText().isBlank();
            boolean hasSupplementaryImage = task.getSupplementaryImageUrl() != null && !task.getSupplementaryImageUrl().isBlank();
            if (hasSupplementaryText || hasSupplementaryImage) {
                if (!hasExtras) {
                    sb.append("# Module-Specific Instructions\n\n");
                    hasExtras = true;
                }
                sb.append(task.getModuleCode()).append(" ").append(task.getModuleName()).append(":\n");
                if (hasSupplementaryText) {
                    sb.append("- User text: ").append(task.getSupplementaryText()).append("\n");
                }
                if (hasSupplementaryImage) {
                    sb.append("- Supplementary reference image(s): ").append(task.getSupplementaryImageUrl()).append("\n");
                    sb.append("  Use these image(s) only for this module's specific product detail, scene, fabric, fit, layout, or care instruction. Do not copy unrelated brands, models, faces, logos, or exact scenes.\n");
                }
                sb.append("\n");
            }
        }
        sb.append("# Task\n");
        sb.append("Create the A+ module brief now. Make every module visually distinct, but keep the product and brand system consistent. ");
        if (hasAplusReference) {
            sb.append("The image generator may receive an A+ reference image, but it is only for layout and presentation style. Write concrete production directions that replace the reference product with the user's product information.\n");
        } else {
            sb.append("No A+ reference image will be passed to the image generator. Write concrete text-to-image production directions based on product information, module instructions, and any supplementary images.\n");
        }
        return sb.toString();
    }

    private Set<String> readSelectedModules(String selectedModulesJson) {
        try {
            List<String> codes = objectMapper.readValue(
                    selectedModulesJson != null ? selectedModulesJson : "[]",
                    new TypeReference<List<String>>() {});
            Set<String> selected = new LinkedHashSet<>(codes);
            return selected.isEmpty() ? AplusModuleDefinition.MODULES.keySet() : selected;
        } catch (Exception e) {
            return AplusModuleDefinition.MODULES.keySet();
        }
    }

    private CopyParseResult parseModuleCopy(String rawCopy) {
        Map<String, String> jsonResult = parseJsonCopy(rawCopy);
        if (!jsonResult.isEmpty()) {
            return new CopyParseResult(jsonResult, toDisplayMarkdown(jsonResult));
        }
        Map<String, String> markdownResult = parseMarkdown(rawCopy);
        return new CopyParseResult(markdownResult,
                markdownResult.isEmpty() ? (rawCopy == null ? "" : rawCopy.trim()) : toDisplayMarkdown(markdownResult));
    }

    private Map<String, String> parseJsonCopy(String rawCopy) {
        Map<String, String> result = new HashMap<>();
        String json = extractJson(rawCopy);
        if (json == null) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode modulesNode = root.path("modules");
            if (!modulesNode.isArray()) {
                return result;
            }
            for (JsonNode moduleNode : modulesNode) {
                String code = moduleNode.path("code").asText("").trim();
                if (!AplusModuleDefinition.MODULES.containsKey(code)) {
                    continue;
                }
                result.put(code, buildModuleCopyFromJson(code, moduleNode));
            }
        } catch (Exception e) {
            log.warn("[A+] JSON copy parse failed: {}", e.getMessage());
            return Map.of();
        }
        return result;
    }

    private String buildModuleCopyFromJson(String code, JsonNode moduleNode) {
        String moduleName = firstText(moduleNode.path("moduleName"), AplusModuleDefinition.MODULES.getOrDefault(code, code));
        String coreSellingPoint = firstText(moduleNode.path("coreSellingPoint"), "Comfortable everyday style with reliable product details.");
        String visualDescription = firstText(moduleNode.path("visualDescription"), AplusModuleDefinition.VISUAL_POSITIONS.getOrDefault(code, ""));
        String copyText = copyTextToLine(moduleNode.path("copyText"));
        String qualityGuards = firstText(moduleNode.path("qualityGuards"),
                "Preserve the source garment silhouette, color family, fabric texture, neckline, sleeve shape, hem, and visible construction details.");
        return "## " + code + " " + stripCjk(moduleName) + "\n"
                + "**Core Selling Point**: " + stripCjk(coreSellingPoint) + "\n"
                + "**Visual Description**: " + stripCjk(visualDescription) + "\n"
                + "**Copy Text**: " + stripCjk(copyText) + "\n"
                + "**Quality Guards**: " + stripCjk(qualityGuards) + "\n";
    }

    private String copyTextToLine(JsonNode copyTextNode) {
        if (copyTextNode == null || copyTextNode.isMissingNode() || copyTextNode.isNull()) {
            return "Soft Comfort; Flattering Fit; Everyday Style";
        }
        if (copyTextNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : copyTextNode) {
                String text = item.asText("").trim();
                if (!text.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("; ");
                    }
                    sb.append(text);
                }
            }
            return !sb.isEmpty() ? sb.toString() : "Soft Comfort; Flattering Fit; Everyday Style";
        }
        return copyTextNode.asText("Soft Comfort; Flattering Fit; Everyday Style");
    }

    private String toDisplayMarkdown(Map<String, String> moduleContents) {
        StringBuilder sb = new StringBuilder();
        for (String code : AplusModuleDefinition.MODULES.keySet()) {
            String content = moduleContents.get(code);
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(content.trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String firstText(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String text = node.asText("").trim();
        return text.isBlank() ? fallback : text;
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?is)^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("(?is)\\s*```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }

    private Map<String, String> parseMarkdown(String markdown) {
        Map<String, String> result = new HashMap<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }
        Matcher matcher = MODULE_PATTERN.matcher(markdown);
        while (matcher.find()) {
            result.put(matcher.group(1), stripCjk(matcher.group(0).trim()));
        }
        return result;
    }

    private String buildFallbackModuleCopy(AplusImageTask task, AplusProject project) {
        String sellingPoint = safeEnglishBenefit(project.getSellingPoints());
        return "## " + task.getModuleCode() + " " + task.getModuleName() + "\n"
                + "**Core Selling Point**: " + sellingPoint + "\n"
                + "**Visual Description**: " + AplusModuleDefinition.VISUAL_POSITIONS.getOrDefault(task.getModuleCode(), "") + "\n"
                + "**Copy Text**: " + sellingPoint + "\n"
                + "**Quality Guards**: Preserve the source garment silhouette, print, fabric texture, color family, and visible construction details from the available product information.\n";
    }

    private String safeEnglishBenefit(String text) {
        String fallback = "Soft comfort, flattering fit, and easy everyday styling.";
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String oneLine = text.replace("\r", " ").replace("\n", " ").trim();
        if (containsCjk(oneLine)) {
            return fallback;
        }
        return oneLine.length() > 180 ? oneLine.substring(0, 180) : oneLine;
    }

    private boolean containsCjk(String text) {
        return text != null && text.codePoints().anyMatch(cp -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
        });
    }

    private boolean hasAplusReference(AplusProject project) {
        return project.getReferenceImageUrl() != null && !project.getReferenceImageUrl().isBlank();
    }

    private String stripCjk(String text) {
        if (text == null || text.isBlank() || !containsCjk(text)) {
            return text;
        }
        String stripped = text.codePoints()
                .filter(cp -> {
                    Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                    return script != Character.UnicodeScript.HAN
                            && script != Character.UnicodeScript.HIRAGANA
                            && script != Character.UnicodeScript.KATAKANA
                            && script != Character.UnicodeScript.HANGUL;
                })
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replaceAll("\\s+", " ")
                .trim();
        return stripped.isBlank() ? "Soft comfort, flattering fit, and easy everyday styling." : stripped;
    }

    private record CopyParseResult(Map<String, String> moduleContents, String displayMarkdown) {
    }
}
