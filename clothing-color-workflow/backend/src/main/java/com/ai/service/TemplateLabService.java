package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.dto.TemplateLabProjectCreateRequest;
import com.ai.dto.TemplateLabProjectResponse;
import com.ai.dto.TemplateLabProjectUpdateRequest;
import com.ai.dto.TemplateLabTemplateCreateRequest;
import com.ai.entity.TemplateLabPersonalTemplate;
import com.ai.entity.TemplateLabProject;
import com.ai.exception.BusinessException;
import com.ai.repository.TemplateLabPersonalTemplateRepository;
import com.ai.repository.TemplateLabProjectRepository;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateLabService {

    private static final long MAX_ASSET_BYTES = 25L * 1024 * 1024;
    private static final int MAX_DESIGN_JSON_LENGTH = 5_000_000;
    private static final int MAX_THUMBNAIL_LENGTH = 1_500_000;
    private static final int MAX_TEMPLATE_JSON_LENGTH = 500_000;

    private final TemplateLabProjectRepository projectRepository;
    private final TemplateLabPersonalTemplateRepository personalTemplateRepository;
    private final ObjectMapper objectMapper;
    private final OssService ossService;
    private final AppProperties appProperties;

    private List<JsonNode> templates = List.of();
    private Map<String, JsonNode> templateIndex = Map.of();

    @PostConstruct
    void loadTemplates() {
        try (InputStream input = new ClassPathResource("template-lab/templates.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            if (!root.isArray() || root.isEmpty()) {
                throw new IllegalStateException("Template Lab template configuration is empty");
            }
            List<JsonNode> loaded = new ArrayList<>();
            root.forEach(template -> {
                validateTemplateDefinition(template);
                loaded.add(template);
            });
            this.templates = List.copyOf(loaded);
            this.templateIndex = Collections.unmodifiableMap(this.templates.stream().collect(Collectors.toMap(
                    item -> item.path("id").asText(), Function.identity(), (left, right) -> left,
                    LinkedHashMap::new)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load Template Lab templates", e);
        }
    }

    @Transactional(readOnly = true)
    public List<JsonNode> listTemplates(Long userId) {
        List<JsonNode> result = new ArrayList<>();
        templates.forEach(template -> {
            ObjectNode item = template.deepCopy();
            item.put("source", "system");
            result.add(item);
        });
        personalTemplateRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::personalTemplateNode)
                .forEach(result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public List<TemplateLabProjectResponse> listProjects(Long userId) {
        return projectRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(project -> TemplateLabProjectResponse.from(project, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateLabProjectResponse getProject(Long id, Long userId) {
        return TemplateLabProjectResponse.from(requireOwnedProject(id, userId), true);
    }

    @Transactional
    public TemplateLabProjectResponse createProject(TemplateLabProjectCreateRequest request,
                                                    Long userId,
                                                    String operator,
                                                    String shopName) {
        JsonNode template = requireTemplate(request.getTemplateId(), userId);
        TemplateLabProject project = new TemplateLabProject();
        project.setOwnerUserId(userId);
        project.setOperator(normalizeIdentity(operator, "用户"));
        project.setShopName(normalizeIdentity(shopName, "默认店铺"));
        project.setProjectName(normalizeProjectName(request.getProjectName(), template.path("name").asText("拼图项目")));
        project.setTemplateId(template.path("id").asText());
        project.setTemplateDefinitionJson(writeJson(template));
        project.setCanvasWidth(template.path("width").asInt(1200));
        project.setCanvasHeight(template.path("height").asInt(1200));
        project.setDesignJson(null);
        return TemplateLabProjectResponse.from(projectRepository.save(project), true);
    }

    @Transactional
    public TemplateLabProjectResponse updateProject(Long id, Long userId, TemplateLabProjectUpdateRequest request) {
        TemplateLabProject project = requireOwnedProject(id, userId);
        if (request.getProjectName() != null) {
            project.setProjectName(normalizeProjectName(request.getProjectName(), project.getProjectName()));
        }
        if (request.getCanvasWidth() != null || request.getCanvasHeight() != null) {
            int width = request.getCanvasWidth() == null ? project.getCanvasWidth() : request.getCanvasWidth();
            int height = request.getCanvasHeight() == null ? project.getCanvasHeight() : request.getCanvasHeight();
            if (width < 320 || height < 320 || width > 8000 || height > 8000) {
                throw new BusinessException("画布尺寸必须在 320 到 8000 像素之间");
            }
            project.setCanvasWidth(width);
            project.setCanvasHeight(height);
        }
        if (request.getDesignJson() != null) {
            validateDesignJson(request.getDesignJson());
            project.setDesignJson(request.getDesignJson());
        }
        if (request.getThumbnailDataUrl() != null) {
            validateThumbnail(request.getThumbnailDataUrl());
            project.setThumbnailDataUrl(request.getThumbnailDataUrl());
        }
        return TemplateLabProjectResponse.from(projectRepository.save(project), true);
    }

    @Transactional
    public TemplateLabProjectResponse duplicateProject(Long id, Long userId) {
        TemplateLabProject source = requireOwnedProject(id, userId);
        TemplateLabProject copy = new TemplateLabProject();
        copy.setOwnerUserId(source.getOwnerUserId());
        copy.setOperator(source.getOperator());
        copy.setShopName(source.getShopName());
        copy.setProjectName(trimToLength(source.getProjectName() + " 副本", 160));
        copy.setTemplateId(source.getTemplateId());
        copy.setTemplateDefinitionJson(source.getTemplateDefinitionJson());
        copy.setCanvasWidth(source.getCanvasWidth());
        copy.setCanvasHeight(source.getCanvasHeight());
        copy.setDesignJson(source.getDesignJson());
        copy.setThumbnailDataUrl(source.getThumbnailDataUrl());
        return TemplateLabProjectResponse.from(projectRepository.save(copy), true);
    }

    @Transactional
    public void deleteProject(Long id, Long userId) {
        projectRepository.delete(requireOwnedProject(id, userId));
    }

    @Transactional
    public JsonNode createPersonalTemplate(Long projectId,
                                           Long userId,
                                           TemplateLabTemplateCreateRequest request) {
        TemplateLabProject project = requireOwnedProject(projectId, userId);
        ObjectNode definition = parseTemplateDefinition(request.getDefinitionJson());
        definition.put("schemaVersion", 1);
        definition.put("name", normalizeTemplateName(request.getName()));
        definition.put("category", normalizeCategory(request.getCategory()));
        definition.put("description", normalizeDescription(request.getDescription()));
        definition.put("width", project.getCanvasWidth());
        definition.put("height", project.getCanvasHeight());
        definition.remove(List.of("id", "source", "personalTemplateId", "thumbnailDataUrl"));
        validateTemplateDefinition(definition);
        validateThumbnail(request.getThumbnailDataUrl());

        TemplateLabPersonalTemplate personal = new TemplateLabPersonalTemplate();
        personal.setOwnerUserId(userId);
        personal.setName(definition.path("name").asText());
        personal.setCategory(definition.path("category").asText());
        personal.setDescription(definition.path("description").asText());
        personal.setDefinitionJson(writeJson(definition));
        personal.setThumbnailDataUrl(request.getThumbnailDataUrl());
        return personalTemplateNode(personalTemplateRepository.save(personal));
    }

    @Transactional
    public void deletePersonalTemplate(Long templateId, Long userId) {
        TemplateLabPersonalTemplate template = personalTemplateRepository.findByIdAndOwnerUserId(templateId, userId)
                .orElseThrow(() -> new BusinessException("个人模板不存在或无权访问"));
        personalTemplateRepository.delete(template);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> uploadAsset(Long projectId, Long userId, MultipartFile file) {
        TemplateLabProject project = requireOwnedProject(projectId, userId);
        validateAsset(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
        String safeShop = project.getShopName().replaceAll("[^\\p{L}\\p{N}_-]", "_");
        String objectName = "TEMPLATE_LAB/" + safeShop + "/" + userId + "/" + projectId + "/"
                + UUID.randomUUID() + extension;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            ossService.getOssClient().putObject(
                    appProperties.getOss().getResultBucket(), objectName, file.getInputStream(), metadata);
            String url = appProperties.getOss().getResultPublicHost() + "/" + objectName;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", url);
            result.put("name", trimToLength(file.getOriginalFilename(), 200));
            result.put("size", file.getSize());
            result.put("contentType", contentType);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("图片上传失败，请稍后重试", e);
        }
    }

    TemplateLabProject requireOwnedProject(Long id, Long userId) {
        return projectRepository.findByIdAndOwnerUserId(id, userId)
                .orElseThrow(() -> new BusinessException("拼图项目不存在或无权访问"));
    }

    private JsonNode requireTemplate(String templateId, Long userId) {
        if (templateId != null && templateId.startsWith("personal:")) {
            Long personalId;
            try {
                personalId = Long.valueOf(templateId.substring("personal:".length()));
            } catch (NumberFormatException error) {
                throw new BusinessException("个人模板编号不正确");
            }
            TemplateLabPersonalTemplate personal = personalTemplateRepository
                    .findByIdAndOwnerUserId(personalId, userId)
                    .orElseThrow(() -> new BusinessException("个人模板不存在或无权访问"));
            return personalTemplateNode(personal);
        }
        JsonNode template = templateIndex.get(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在: " + templateId);
        }
        return template;
    }

    private JsonNode personalTemplateNode(TemplateLabPersonalTemplate template) {
        ObjectNode definition = parseTemplateDefinition(template.getDefinitionJson());
        definition.put("id", "personal:" + template.getId());
        definition.put("source", "personal");
        definition.put("personalTemplateId", template.getId());
        definition.put("name", template.getName());
        definition.put("category", template.getCategory());
        definition.put("description", template.getDescription());
        if (template.getThumbnailDataUrl() != null && !template.getThumbnailDataUrl().isBlank()) {
            definition.put("thumbnailDataUrl", template.getThumbnailDataUrl());
        }
        return definition;
    }

    private ObjectNode parseTemplateDefinition(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_TEMPLATE_JSON_LENGTH) {
            throw new BusinessException("模板数据为空或体积过大");
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!(root instanceof ObjectNode object)) {
                throw new BusinessException("模板数据格式不正确");
            }
            return object.deepCopy();
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("模板数据格式不正确");
        }
    }

    private void validateTemplateDefinition(JsonNode definition) {
        int width = definition.path("width").asInt();
        int height = definition.path("height").asInt();
        String usageType = definition.path("usageType").asText();
        String ratioGroup = definition.path("ratioGroup").asText();
        JsonNode frames = definition.path("frames");
        JsonNode texts = definition.path("texts");
        if (width < 320 || height < 320 || width > 8000 || height > 8000) {
            throw new BusinessException("模板画布尺寸必须在 320 到 8000 像素之间");
        }
        if (!frames.isArray() || frames.isEmpty() || frames.size() > 20) {
            throw new BusinessException("模板必须包含 1 到 20 个相框");
        }
        if (!texts.isArray() || texts.size() > 40) {
            throw new BusinessException("模板文字字段不能超过 40 个");
        }
        validateTemplateRatio(usageType, ratioGroup, width, height);
        for (JsonNode frame : frames) {
            if (frame.path("id").asText().isBlank()
                    || frame.path("width").asDouble() <= 0
                    || frame.path("height").asDouble() <= 0) {
                throw new BusinessException("模板包含无效相框");
            }
        }
    }

    private void validateTemplateRatio(String usageType, String ratioGroup, int width, int height) {
        boolean valid = switch (usageType) {
            case "副图" -> switch (ratioGroup) {
                case "1:1" -> width == height;
                case "3:4" -> (long) width * 4 == (long) height * 3;
                case "16:9" -> (long) width * 9 == (long) height * 16;
                default -> false;
            };
            case "亚马逊 A+" -> switch (ratioGroup) {
                case "2928:1200" -> width == 2928 && height == 1200;
                case "1200:900" -> width == 1200 && height == 900;
                default -> false;
            };
            default -> false;
        };
        if (!valid) {
            throw new BusinessException("模板尺寸不在支持范围：副图仅支持 1:1、3:4、16:9，亚马逊 A+ 仅支持 2928:1200、1200:900");
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("模板数据序列化失败", error);
        }
    }

    private String normalizeTemplateName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new BusinessException("请输入模板名称");
        return trimToLength(normalized, 160);
    }

    private String normalizeCategory(String value) {
        String normalized = value == null ? "" : value.trim();
        return trimToLength(normalized.isEmpty() ? "个人模板" : normalized, 80);
    }

    private String normalizeDescription(String value) {
        String normalized = value == null ? "" : value.trim();
        return trimToLength(normalized.isEmpty() ? "从个人项目保存的可复用模板。" : normalized, 500);
    }

    private void validateDesignJson(String value) {
        if (value.length() > MAX_DESIGN_JSON_LENGTH) {
                throw new BusinessException("项目数据过大，请减少画布元素后重试");
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isObject()) {
            throw new BusinessException("项目数据格式不正确");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("项目数据格式不正确");
        }
    }

    private void validateThumbnail(String value) {
        if (value == null || value.isBlank()) return;
        if (value.length() > MAX_THUMBNAIL_LENGTH || !value.startsWith("data:image/")) {
            throw new BusinessException("项目预览图格式不正确或体积过大");
        }
    }

    private void validateAsset(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_ASSET_BYTES) {
            throw new BusinessException("单张图片不能超过 25MB");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(type)) {
            throw new BusinessException("仅支持 JPG、PNG 和 WebP 图片");
        }
    }

    private String normalizeProjectName(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallback + " " + DateTimeFormatter.ofPattern("MM-dd HHmm")
                    .format(java.time.LocalDateTime.now());
        }
        return trimToLength(normalized, 160);
    }

    private String normalizeIdentity(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return trimToLength(normalized.isEmpty() ? fallback : normalized, 100);
    }

    private String trimToLength(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}
