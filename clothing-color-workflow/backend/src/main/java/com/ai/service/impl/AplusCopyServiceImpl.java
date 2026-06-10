package com.ai.service.impl;

import com.ai.dto.AplusModuleDefinition;
import com.ai.dto.ModelGenerateRequest;
import com.ai.entity.AplusImageTask;
import com.ai.entity.AplusProject;
import com.ai.enums.AplusProjectStatus;
import com.ai.repository.AplusImageTaskRepository;
import com.ai.repository.AplusProjectRepository;
import com.ai.service.AplusCopyService;
import com.ai.service.TextModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
        AplusProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("A+ 项目不存在: " + projectId));

        project.setStatus(AplusProjectStatus.GENERATING_COPY.name());
        project.setErrorMessage(null);
        projectRepository.save(project);

        try {
            String systemPrompt = buildSystemPrompt(readSkillTemplate(), project.getSelectedModules());
            String userPrompt = buildUserPrompt(project);

            ModelGenerateRequest request = new ModelGenerateRequest();
            request.setSpecialRequirements(systemPrompt + "\n\n---\n\n" + userPrompt);
            request.setTextModel("claude");

            log.info("[A+] generating copy: projectId={}", projectId);
            String markdown = textModelService.generatePrompt(request, "claude");
            Map<String, String> moduleContents = parseMarkdown(markdown);

            project.setAplusMarkdown(markdown);
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
        if (skillTemplatePath == null || skillTemplatePath.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(skillTemplatePath);
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (IOException e) {
            log.warn("[A+] read skill template failed: {}", e.getMessage());
            return "";
        }
    }

    private String buildSystemPrompt(String skillTemplate, String selectedModulesJson) {
        Set<String> selectedCodes = readSelectedModules(selectedModulesJson);
        StringBuilder sb = new StringBuilder();
        sb.append("# Role\n");
        sb.append("You are an expert e-commerce product-detail content copywriter and visual director for women's fashion.\n\n");
        if (skillTemplate != null && !skillTemplate.isBlank()) {
            sb.append("# Skill Template Reference\n").append(skillTemplate).append("\n\n");
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
        sb.append("# Style Anchor\n");
        sb.append(AplusModuleDefinition.STYLE_ANCHOR).append("\n\n");
        sb.append("# Output Contract\n");
        sb.append("Return Markdown only. Output exactly one section for each selected module.\n");
        sb.append("Use this exact heading format: ## AD-XX Module Name\n");
        sb.append("Each module must include:\n");
        sb.append("**Core Selling Point**: one concise English line\n");
        sb.append("**Visual Description**: detailed English visual direction for image generation\n");
        sb.append("**Copy Text**: final English marketing copy\n");
        sb.append("For AD-06 and AD-07, do not ask the image model to render readable text. Describe clean blank layout zones for system text overlay.\n");
        return sb.toString();
    }

    private String buildUserPrompt(AplusProject project) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Product Information\n\n");
        sb.append("Reference Image: ").append(project.getReferenceImageUrl()).append("\n\n");
        sb.append("Selling Points:\n").append(project.getSellingPoints()).append("\n\n");

        List<AplusImageTask> tasks = imageTaskRepository.findByProjectId(project.getId());
        boolean hasExtras = false;
        for (AplusImageTask task : tasks) {
            if (task.getSupplementaryText() != null && !task.getSupplementaryText().isBlank()) {
                if (!hasExtras) {
                    sb.append("# Module-Specific Instructions\n\n");
                    hasExtras = true;
                }
                sb.append(task.getModuleCode()).append(" ").append(task.getModuleName()).append(":\n");
                sb.append(task.getSupplementaryText()).append("\n\n");
            }
        }
        sb.append("Generate the A+ content now. Keep the product, print, fabric, color system, and brand tone consistent across all modules.\n");
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

    private Map<String, String> parseMarkdown(String markdown) {
        Map<String, String> result = new HashMap<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }
        Matcher matcher = MODULE_PATTERN.matcher(markdown);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(0).trim());
        }
        return result;
    }

    private String buildFallbackModuleCopy(AplusImageTask task, AplusProject project) {
        String sellingPoint = safeOneLine(project.getSellingPoints());
        return "## " + task.getModuleCode() + " " + task.getModuleName() + "\n"
                + "**Core Selling Point**: " + sellingPoint + "\n"
                + "**Visual Description**: " + AplusModuleDefinition.VISUAL_POSITIONS.getOrDefault(task.getModuleCode(), "") + "\n"
                + "**Copy Text**: " + sellingPoint + "\n";
    }

    private String safeOneLine(String text) {
        if (text == null || text.isBlank()) {
            return "Highlight the product's key benefits with a clean, premium fashion e-commerce tone.";
        }
        String oneLine = text.replace("\r", " ").replace("\n", " ").trim();
        return oneLine.length() > 180 ? oneLine.substring(0, 180) : oneLine;
    }
}
