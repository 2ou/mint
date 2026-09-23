package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.dto.TemplateLabProjectCreateRequest;
import com.ai.dto.TemplateLabProjectResponse;
import com.ai.dto.TemplateLabProjectUpdateRequest;
import com.ai.dto.TemplateLabTemplateCreateRequest;
import com.ai.dto.KieTaskResult;
import com.ai.entity.TemplateLabPersonalTemplate;
import com.ai.entity.TemplateLabProject;
import com.ai.exception.BusinessException;
import com.ai.repository.TemplateLabPersonalTemplateRepository;
import com.ai.repository.TemplateLabProjectRepository;
import com.ai.service.impl.KieGptModels;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
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
@Slf4j
public class TemplateLabService {

    private static final long MAX_ASSET_BYTES = 25L * 1024 * 1024;
    private static final long MAX_CUTOUT_INPUT_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DESIGN_JSON_LENGTH = 5_000_000;
    private static final int MAX_THUMBNAIL_LENGTH = 1_500_000;
    private static final int MAX_TEMPLATE_JSON_LENGTH = 500_000;
    private static final String CUTOUT_MODEL = "qwen2-1/image-to-image";
    private static final String TEMPLATE_PARSE_MODEL = KieGptModels.GPT_5_6_SOL;

    private final TemplateLabProjectRepository projectRepository;
    private final TemplateLabPersonalTemplateRepository personalTemplateRepository;
    private final ObjectMapper objectMapper;
    private final OssService ossService;
    private final AppProperties appProperties;
    private final Environment environment;
    private final KieClientService kieClientService;
    private final CanvasTaskService canvasTaskService;
    private final ModelPricingService modelPricingService;
    private final TextModelService textModelService;

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
        personalTemplateRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(template -> personalTemplateNode(template, userId))
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
    public TemplateLabProjectResponse parseTemplateImage(MultipartFile file,
                                                         String canvasSpec,
                                                         String projectName,
                                                         String notes,
                                                         Long userId,
                                                         String operator,
                                                         String shopName) {
        validateAsset(file);
        ParsedCanvasSpec spec = requireParsedCanvasSpec(canvasSpec);
        String normalizedShop = normalizeIdentity(shopName, "默认店铺");
        StoredTemplateSource source = storeTemplateSource(file, userId, normalizedShop);
        String temporaryProviderObject = null;
        try {
            String providerUrl = source.servingUrl();
            if (useLocalAssetStorage()) {
                temporaryProviderObject = uploadTemplateParseInput(file, userId, normalizedShop);
                providerUrl = publicOssUrl(temporaryProviderObject);
            }
            String raw = textModelService.generateRawPromptWithImages(
                    templateParserSystemPrompt(),
                    templateParserUserPrompt(spec, notes),
                    List.of(providerUrl),
                    TEMPLATE_PARSE_MODEL);
            ObjectNode definition = buildParsedTemplateDefinition(
                    parseTemplateModelJson(raw), spec, source.servingUrl(), file.getOriginalFilename());
            validateTemplateDefinition(definition);

            TemplateLabProject project = new TemplateLabProject();
            project.setOwnerUserId(userId);
            project.setOperator(normalizeIdentity(operator, "用户"));
            project.setShopName(normalizedShop);
            project.setProjectName(normalizeProjectName(projectName, "图片解析模板"));
            project.setTemplateId(definition.path("id").asText());
            project.setTemplateDefinitionJson(writeJson(definition));
            project.setCanvasWidth(spec.width());
            project.setCanvasHeight(spec.height());
            project.setDesignJson(null);
            project.setThumbnailDataUrl(source.servingUrl());
            return TemplateLabProjectResponse.from(projectRepository.save(project), true);
        } catch (BusinessException error) {
            deleteStoredTemplateSource(source);
            throw error;
        } catch (Exception error) {
            deleteStoredTemplateSource(source);
            log.warn("模板图片解析失败", error);
            throw new BusinessException("图片解析失败，请检查图片是否清晰并重试");
        } finally {
            deleteOssObjectQuietly(temporaryProviderObject);
        }
    }

    private StoredTemplateSource storeTemplateSource(MultipartFile file, Long userId, String shopName) {
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = imageExtension(contentType);
        String sourceId = UUID.randomUUID().toString();
        String safeShop = safePathSegment(shopName);
        if (useLocalAssetStorage()) {
            Path root = localSaveRoot();
            Path directory = root.resolve("template-lab")
                    .resolve(safeShop)
                    .resolve(String.valueOf(userId))
                    .resolve("template-sources")
                    .normalize();
            if (!directory.startsWith(root)) {
                throw new IllegalStateException("模板来源图片目录不安全");
            }
            Path target = directory.resolve(sourceId + extension).normalize();
            try {
                Files.createDirectories(directory);
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, target);
                }
                return new StoredTemplateSource(localServingUrl(root, target), target, null);
            } catch (Exception error) {
                try {
                    Files.deleteIfExists(target);
                } catch (Exception ignored) {
                    // Preserve the original storage error.
                }
                throw new IllegalStateException("模板来源图片保存失败，请检查本地结果目录是否可写", error);
            }
        }

        String objectName = "TEMPLATE_LAB/" + safeShop + "/" + userId
                + "/template-sources/" + sourceId + extension;
        uploadOssFile(file, contentType, objectName);
        return new StoredTemplateSource(publicOssUrl(objectName), null, objectName);
    }

    private String uploadTemplateParseInput(MultipartFile file, Long userId, String shopName) {
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String objectName = "TEMPLATE_LAB/" + safePathSegment(shopName) + "/" + userId
                + "/template-parse-input/" + UUID.randomUUID() + imageExtension(contentType);
        uploadOssFile(file, contentType, objectName);
        return objectName;
    }

    private void uploadOssFile(MultipartFile file, String contentType, String objectName) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            ossService.getOssClient().putObject(
                    appProperties.getOss().getResultBucket(), objectName, file.getInputStream(), metadata);
        } catch (Exception error) {
            throw new IllegalStateException("图片上传到解析存储失败", error);
        }
    }

    private String publicOssUrl(String objectName) {
        String host = appProperties.getOss().getResultPublicHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("OSS 公网访问地址未配置");
        }
        return host.replaceAll("/+$", "") + "/" + objectName;
    }

    private void deleteStoredTemplateSource(StoredTemplateSource source) {
        if (source == null) return;
        if (source.localPath() != null) {
            try {
                Files.deleteIfExists(source.localPath());
            } catch (Exception error) {
                log.warn("模板来源图片清理失败: {}", source.localPath(), error);
            }
        }
        deleteOssObjectQuietly(source.ossObjectName());
    }

    private void deleteOssObjectQuietly(String objectName) {
        if (objectName == null || objectName.isBlank()) return;
        try {
            ossService.getOssClient().deleteObject(appProperties.getOss().getResultBucket(), objectName);
        } catch (Exception error) {
            log.warn("模板解析临时对象清理失败: {}", objectName, error);
        }
    }

    private String imageExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private String templateParserSystemPrompt() {
        return """
                You analyze an ecommerce collage image and return strict JSON only. Do not use markdown.
                Reconstruct only editable photo/product regions and OCR text. Decorative color blocks, lines,
                icons, shadows, textures and ornaments remain baked into the supplied background image.
                Coordinates must describe the final target canvas after the source image is centered with
                object-fit: cover. For each editable region, estimate a solid coverFill sampled from the
                surrounding background so the original photo or text can be covered before the editable layer.
                Never invent text. Return at most 20 frames and 40 text items.
                """;
    }

    private String templateParserUserPrompt(ParsedCanvasSpec spec, String notes) {
        String normalizedNotes = notes == null ? "" : trimToLength(notes.trim(), 1000);
        return """
                Target canvas: %d x %d pixels. Usage: %s. Ratio: %s.
                Optional user notes: %s
                Return exactly this JSON shape:
                {
                  "background":"#RRGGBB",
                  "accent":"#RRGGBB",
                  "frames":[{"label":"商品图 1","shape":"rect|rounded|circle","x":0,"y":0,"width":100,"height":100,"radius":0,"rotation":0,"coverFill":"#RRGGBB"}],
                  "texts":[{"label":"标题","text":"exact OCR text","x":0,"y":0,"width":100,"height":40,"fontSize":24,"fontWeight":"400|500|600|700","fontFamily":"Arial|Microsoft YaHei|SimHei|SimSun|Georgia","fill":"#RRGGBB","textAlign":"left|center|right","lineHeight":1.1,"rotation":0,"coverFill":"#RRGGBB"}]
                }
                Coordinates are top-left based and must stay within the target canvas. Include only regions that
                should be replaceable/editable. A useful template must contain at least one photo/product frame.
                """.formatted(spec.width(), spec.height(), spec.usageType(), spec.ratioGroup(),
                normalizedNotes.isBlank() ? "none" : normalizedNotes);
    }

    private ObjectNode parseTemplateModelJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("KIE 未返回模板解析结果，请重试");
        }
        String normalized = raw.trim();
        int first = normalized.indexOf('{');
        int last = normalized.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new BusinessException("KIE 返回的模板结构无法读取，请重试");
        }
        try {
            JsonNode root = objectMapper.readTree(normalized.substring(first, last + 1));
            if (!(root instanceof ObjectNode object)) {
                throw new BusinessException("KIE 返回的模板结构无法读取，请重试");
            }
            return object;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("KIE 返回的模板结构无法读取，请重试");
        }
    }

    private ObjectNode buildParsedTemplateDefinition(ObjectNode detected,
                                                       ParsedCanvasSpec spec,
                                                       String sourceUrl,
                                                       String originalFilename) {
        ObjectNode definition = objectMapper.createObjectNode();
        String parsedId = "parsed:" + UUID.randomUUID().toString().substring(0, 12);
        String background = normalizeHexColor(detected.path("background").asText(), "#f5f5f5");
        String accent = normalizeHexColor(detected.path("accent").asText(), "#172033");
        definition.put("schemaVersion", 1);
        definition.put("id", parsedId);
        definition.put("name", "图片解析模板");
        definition.put("category", "AI 解析");
        definition.put("description", "由上传图片识别生成的草稿，请检查图片位、遮罩和文字后再保存为个人模板。");
        definition.put("usageType", spec.usageType());
        definition.put("ratioGroup", spec.ratioGroup());
        definition.put("width", spec.width());
        definition.put("height", spec.height());
        definition.put("background", background);
        definition.put("accent", accent);
        ArrayNode tags = definition.putArray("tags");
        tags.add("AI 解析").add(spec.usageType()).add(spec.ratioGroup());
        ArrayNode frames = definition.putArray("frames");
        ArrayNode texts = definition.putArray("texts");
        ArrayNode masks = definition.putArray("backgroundMasks");
        definition.putArray("images");

        JsonNode detectedFrames = detected.path("frames");
        if (detectedFrames.isArray()) {
            int count = Math.min(20, detectedFrames.size());
            for (int index = 0; index < count; index++) {
                JsonNode item = detectedFrames.get(index);
                double x = clamp(item.path("x").asDouble(), 0, spec.width() - 32);
                double y = clamp(item.path("y").asDouble(), 0, spec.height() - 32);
                double width = clamp(item.path("width").asDouble(), 32, spec.width() - x);
                double height = clamp(item.path("height").asDouble(), 32, spec.height() - y);
                String frameId = "parsed-frame-" + (index + 1);
                String shape = normalizeOption(item.path("shape").asText(), List.of("rect", "rounded", "circle"), "rect");
                double radius = "circle".equals(shape) ? Math.min(width, height) / 2
                        : clamp(item.path("radius").asDouble(), 0, Math.min(width, height) / 2);
                double rotation = clamp(item.path("rotation").asDouble(), -180, 180);
                String coverFill = normalizeHexColor(item.path("coverFill").asText(), background);

                ObjectNode frame = frames.addObject();
                frame.put("id", frameId);
                frame.put("slotId", frameId);
                frame.put("label", trimToLength(item.path("label").asText("商品图 " + (index + 1)), 80));
                frame.put("required", true);
                frame.put("replaceable", true);
                frame.put("x", roundCoordinate(x));
                frame.put("y", roundCoordinate(y));
                frame.put("width", roundCoordinate(width));
                frame.put("height", roundCoordinate(height));
                frame.put("shape", shape);
                frame.put("radius", roundCoordinate(radius));
                frame.put("rotation", rotation);
                appendBackgroundMask(masks, "frame-mask-" + (index + 1), x, y, width, height,
                        coverFill, radius, rotation);
            }
        }
        if (frames.isEmpty()) {
            throw new BusinessException("没有识别到可替换的商品或照片区域，请换一张布局更清晰的图片重试");
        }

        JsonNode detectedTexts = detected.path("texts");
        if (detectedTexts.isArray()) {
            int count = Math.min(40, detectedTexts.size());
            for (int index = 0; index < count; index++) {
                JsonNode item = detectedTexts.get(index);
                String content = trimToLength(item.path("text").asText().trim(), 2000);
                if (content.isBlank()) continue;
                double x = clamp(item.path("x").asDouble(), 0, spec.width() - 24);
                double y = clamp(item.path("y").asDouble(), 0, spec.height() - 16);
                double width = clamp(item.path("width").asDouble(), 24, spec.width() - x);
                double height = clamp(item.path("height").asDouble(), 16, spec.height() - y);
                double fontSize = clamp(item.path("fontSize").asDouble(), 10, 240);
                double rotation = clamp(item.path("rotation").asDouble(), -180, 180);
                String textId = "parsed-text-" + (index + 1);
                ObjectNode text = texts.addObject();
                text.put("id", textId);
                text.put("fieldId", textId);
                text.put("label", trimToLength(item.path("label").asText("文字 " + (index + 1)), 80));
                text.put("editable", true);
                text.put("text", content);
                text.put("x", roundCoordinate(x));
                text.put("y", roundCoordinate(y));
                text.put("width", roundCoordinate(width));
                text.put("fontSize", roundCoordinate(fontSize));
                text.put("fontWeight", normalizeOption(item.path("fontWeight").asText(),
                        List.of("400", "500", "600", "700"), "400"));
                text.put("fontFamily", normalizeOption(item.path("fontFamily").asText(),
                        List.of("Arial", "Microsoft YaHei", "SimHei", "SimSun", "Georgia"), "Arial"));
                text.put("fill", normalizeHexColor(item.path("fill").asText(), accent));
                text.put("textAlign", normalizeOption(item.path("textAlign").asText(),
                        List.of("left", "center", "right"), "left"));
                text.put("lineHeight", clamp(item.path("lineHeight").asDouble(1.1), 0.8, 2.5));
                text.put("angle", rotation);

                double padding = Math.min(10, Math.max(3, fontSize * 0.12));
                double maskX = Math.max(0, x - padding);
                double maskY = Math.max(0, y - padding);
                double maskWidth = Math.min(spec.width() - maskX, width + padding * 2);
                double maskHeight = Math.min(spec.height() - maskY, height + padding * 2);
                appendBackgroundMask(masks, "text-mask-" + (index + 1), maskX, maskY, maskWidth,
                        maskHeight, normalizeHexColor(item.path("coverFill").asText(), background), 0, rotation);
            }
        }

        ObjectNode canvasBackground = definition.putObject("canvasBackground");
        canvasBackground.put("url", sourceUrl);
        canvasBackground.put("name", trimToLength(originalFilename, 200));
        canvasBackground.put("opacity", 1);
        canvasBackground.put("zoom", 1);
        canvasBackground.putNull("cropX");
        canvasBackground.putNull("cropY");
        return definition;
    }

    private void appendBackgroundMask(ArrayNode masks,
                                      String id,
                                      double x,
                                      double y,
                                      double width,
                                      double height,
                                      String fill,
                                      double radius,
                                      double angle) {
        ObjectNode mask = masks.addObject();
        mask.put("id", id);
        mask.put("x", roundCoordinate(x));
        mask.put("y", roundCoordinate(y));
        mask.put("width", roundCoordinate(width));
        mask.put("height", roundCoordinate(height));
        mask.put("fill", fill);
        mask.put("radius", roundCoordinate(radius));
        mask.put("angle", angle);
    }

    private ParsedCanvasSpec requireParsedCanvasSpec(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case "secondary-square" -> new ParsedCanvasSpec("secondary-square", "副图", "1:1", 1200, 1200);
            case "secondary-portrait" -> new ParsedCanvasSpec("secondary-portrait", "副图", "3:4", 1200, 1600);
            case "secondary-wide" -> new ParsedCanvasSpec("secondary-wide", "副图", "16:9", 1600, 900);
            case "aplus-wide" -> new ParsedCanvasSpec("aplus-wide", "亚马逊 A+", "2928:1200", 2928, 1200);
            case "aplus-standard" -> new ParsedCanvasSpec("aplus-standard", "亚马逊 A+", "1200:900", 1200, 900);
            default -> throw new BusinessException("请选择有效的模板用途和画布比例");
        };
    }

    private String normalizeHexColor(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.matches("(?i)^#[0-9a-f]{6}$") ? normalized.toLowerCase(Locale.ROOT) : fallback;
    }

    private String normalizeOption(String value, List<String> allowed, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private int roundCoordinate(double value) {
        return (int) Math.round(value);
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
        TemplateLabProject project = requireOwnedProject(id, userId);
        projectRepository.delete(project);
        cleanupProjectAssets(project);
    }

    private void cleanupProjectAssets(TemplateLabProject project) {
        cleanupLocalProjectAssets(project);
        String safeShop = project.getShopName() == null ? "_" : project.getShopName().replaceAll("[^\\p{L}\\p{N}_-]", "_");
        String prefix = "TEMPLATE_LAB/" + safeShop + "/" + project.getOwnerUserId() + "/" + project.getId() + "/";
        try {
            var client = ossService.getOssClient();
            String bucket = appProperties.getOss().getResultBucket();
            String nextMarker = null;
            do {
                var request = new com.aliyun.oss.model.ListObjectsRequest(bucket).withPrefix(prefix).withMaxKeys(1000).withMarker(nextMarker);
                var listing = client.listObjects(request);
                List<String> keys = listing.getObjectSummaries().stream()
                        .map(com.aliyun.oss.model.OSSObjectSummary::getKey)
                        .toList();
                if (!keys.isEmpty()) {
                    client.deleteObjects(new com.aliyun.oss.model.DeleteObjectsRequest(bucket).withKeys(keys));
                }
                nextMarker = listing.getNextMarker();
            } while (nextMarker != null && !nextMarker.isBlank());
        } catch (Exception ignored) {
            // 删除项目时 OSS 清理尽力而为，失败不影响 DB 删除
        }
    }

    private void cleanupLocalProjectAssets(TemplateLabProject project) {
        if (!useLocalAssetStorage()) return;
        Path root = localSaveRoot();
        Path projectDir = localProjectDir(project, root);
        if (!projectDir.startsWith(root) || projectDir.equals(root) || !Files.exists(projectDir)) return;
        try (var paths = Files.walk(projectDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception error) {
                    log.warn("模板实验室本地素材清理失败: {}", path, error);
                }
            });
        } catch (Exception error) {
            log.warn("模板实验室本地项目目录清理失败: {}", projectDir, error);
        }
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
        return personalTemplateNode(personalTemplateRepository.save(personal), userId);
    }

    @Transactional
    public void deletePersonalTemplate(Long templateId, Long userId) {
        TemplateLabPersonalTemplate template = personalTemplateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("个人模板不存在"));
        boolean owner = template.getOwnerUserId() != null && template.getOwnerUserId().equals(userId);
        if (!owner && !isAdmin(userId)) {
            throw new BusinessException("仅作者或管理员可删除该模板");
        }
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
        String assetId = UUID.randomUUID().toString();
        if (useLocalAssetStorage()) {
            return saveLocalAsset(project, file, contentType, extension, assetId);
        }
        String safeShop = safePathSegment(project.getShopName());
        String objectPrefix = "TEMPLATE_LAB/" + safeShop + "/" + userId + "/" + projectId;
        String objectName = objectPrefix + "/assets/" + assetId + extension;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            ossService.getOssClient().putObject(
                    appProperties.getOss().getResultBucket(), objectName, file.getInputStream(), metadata);
            String url = appProperties.getOss().getResultPublicHost() + "/" + objectName;
            String thumbnailUrl = uploadOssThumbnail(file, contentType, extension, objectPrefix, assetId, url);
            return assetResponse(file, contentType, url, thumbnailUrl, "oss");
        } catch (Exception e) {
            throw new IllegalStateException("图片上传失败，请稍后重试", e);
        }
    }

    private Map<String, Object> saveLocalAsset(TemplateLabProject project,
                                               MultipartFile file,
                                               String contentType,
                                               String extension,
                                               String assetId) {
        Path root = localSaveRoot();
        Path projectDir = localProjectDir(project, root);
        Path assetDir = projectDir.resolve("assets").normalize();
        Path thumbnailDir = projectDir.resolve("thumbnails").normalize();
        if (!assetDir.startsWith(root) || !thumbnailDir.startsWith(root)) {
            throw new IllegalStateException("模板实验室本地素材目录不安全");
        }
        Path target = assetDir.resolve(assetId + extension).normalize();
        try {
            Files.createDirectories(assetDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            String url = localServingUrl(root, target);
            String thumbnailUrl = url;
            byte[] thumbnail = createAssetThumbnail(file, contentType);
            if (thumbnail != null) {
                Files.createDirectories(thumbnailDir);
                String thumbnailExtension = "image/png".equals(contentType) ? ".png" : ".jpg";
                Path thumbnailTarget = thumbnailDir.resolve(assetId + thumbnailExtension).normalize();
                Files.write(thumbnailTarget, thumbnail);
                thumbnailUrl = localServingUrl(root, thumbnailTarget);
            }
            return assetResponse(file, contentType, url, thumbnailUrl, "local");
        } catch (Exception error) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
                // Preserve the upload error; partial-file cleanup is best effort.
            }
            throw new IllegalStateException("图片保存到本地失败，请检查 D:\\AiResult 是否可写", error);
        }
    }

    private String uploadOssThumbnail(MultipartFile file,
                                      String contentType,
                                      String extension,
                                      String objectPrefix,
                                      String assetId,
                                      String fallbackUrl) {
        byte[] thumbnail = createAssetThumbnail(file, contentType);
        if (thumbnail == null) return fallbackUrl;
        String thumbnailExtension = "image/png".equals(contentType) ? ".png" : ".jpg";
        String thumbnailType = "image/png".equals(contentType) ? "image/png" : "image/jpeg";
        String thumbnailObject = objectPrefix + "/thumbnails/" + assetId + thumbnailExtension;
        try (ByteArrayInputStream input = new ByteArrayInputStream(thumbnail)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(thumbnailType);
            metadata.setContentLength(thumbnail.length);
            ossService.getOssClient().putObject(
                    appProperties.getOss().getResultBucket(), thumbnailObject, input, metadata);
            return appProperties.getOss().getResultPublicHost() + "/" + thumbnailObject;
        } catch (Exception error) {
            log.warn("模板实验室素材缩略图上传失败，回退原图: {}{}", assetId, extension, error);
            return fallbackUrl;
        }
    }

    private byte[] createAssetThumbnail(MultipartFile file, String contentType) {
        if ("image/webp".equals(contentType)) return null;
        try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var builder = Thumbnails.of(input).size(512, 512);
            if ("image/png".equals(contentType)) {
                builder.outputFormat("png");
            } else {
                builder.outputFormat("jpg").outputQuality(0.82);
            }
            builder.toOutputStream(output);
            return output.toByteArray();
        } catch (Exception error) {
            log.warn("模板实验室素材缩略图生成失败，回退原图: {}", file.getOriginalFilename(), error);
            return null;
        }
    }

    private Map<String, Object> assetResponse(MultipartFile file,
                                              String contentType,
                                              String url,
                                              String thumbnailUrl,
                                              String storage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("thumbnailUrl", thumbnailUrl);
        result.put("name", trimToLength(file.getOriginalFilename(), 200));
        result.put("size", file.getSize());
        result.put("contentType", contentType);
        result.put("storage", storage);
        return result;
    }

    private Path localSaveRoot() {
        String configured = appProperties.getLocalSaveRoot();
        if (configured == null || configured.isBlank()) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            configured = os.contains("win") ? "D:/AiResult" : "/tmp/ai-result";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private boolean useLocalAssetStorage() {
        return appProperties.isTemplateLabLocalAssets()
                || environment.acceptsProfiles(Profiles.of("dev"));
    }

    private Path localProjectDir(TemplateLabProject project, Path root) {
        return root.resolve("template-lab")
                .resolve(safePathSegment(project.getShopName()))
                .resolve(String.valueOf(project.getOwnerUserId()))
                .resolve(String.valueOf(project.getId()))
                .normalize();
    }

    private String localServingUrl(Path root, Path target) {
        String relative = root.relativize(target).toString().replace('\\', '/');
        return "/ai-result/" + relative;
    }

    private String safePathSegment(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        return normalized.isBlank() ? "_" : trimToLength(normalized, 100);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cutoutQuote(Long projectId, Long userId) {
        requireOwnedProject(projectId, userId);
        return modelPricingService.quote("image", Map.of("model", CUTOUT_MODEL, "resolution", "2K"), 1).toMap();
    }

    @Transactional
    public Map<String, Object> createCutout(Long projectId,
                                            Long userId,
                                            String operator,
                                            String shopName,
                                            String elementId,
                                            String sourceUrl,
                                            MultipartFile file) {
        TemplateLabProject project = requireOwnedProject(projectId, userId);
        validateCutoutInput(file);
        String normalizedElementId = trimToLength(elementId, 96);
        if (normalizedElementId == null || normalizedElementId.isBlank()) {
            throw new BusinessException("图片元素编号不能为空");
        }
        canvasTaskService.requireSubmissionCapacity(operator, shopName);

        String safeShop = project.getShopName().replaceAll("[^\\p{L}\\p{N}_-]", "_");
        String objectName = "TEMPLATE_LAB/" + safeShop + "/" + userId + "/" + projectId
                + "/cutout-input/" + UUID.randomUUID() + ".jpg";
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("image/jpeg");
            metadata.setContentLength(file.getSize());
            ossService.getOssClient().putObject(
                    appProperties.getOss().getResultBucket(), objectName, file.getInputStream(), metadata);
            String providerInputUrl = appProperties.getOss().getResultPublicHost() + "/" + objectName;
            Map<String, Object> cutoutInput = new LinkedHashMap<>();
            cutoutInput.put("prompt", "Remove the background and keep only the main subject. "
                    + "Output an RGBA image with a transparent background (alpha channel), preserving the subject's original colors and details.");
            cutoutInput.put("image_urls", List.of(providerInputUrl));
            cutoutInput.put("aspect_ratio", "auto");
            cutoutInput.put("resolution", "2K");
            cutoutInput.put("background", "transparent");
            cutoutInput.put("output_format", "png");
            cutoutInput.put("enhance_prompt", false);
            KieTaskResult created = kieClientService.createMarketTask(CUTOUT_MODEL, cutoutInput);
            String taskId = created.getTaskId();
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalStateException("KIE 未返回抠图任务编号");
            }

            Map<String, Object> taskPayload = new LinkedHashMap<>();
            taskPayload.put("model", CUTOUT_MODEL);
            taskPayload.put("canvas_id", templateCanvasId(projectId));
            taskPayload.put("canvas_node_id", normalizedElementId);
            taskPayload.put("template_lab_project_id", projectId);
            taskPayload.put("source_url", sourceUrl == null ? "" : sourceUrl.trim());
            taskPayload.put("provider_input_url", providerInputUrl);
            taskPayload.put("provider_input_object", objectName);
            canvasTaskService.recordCreated(taskId, "image", operator, shopName, taskPayload);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("task_id", taskId);
            response.put("element_id", normalizedElementId);
            response.put("status", "processing");
            response.put("model", CUTOUT_MODEL);
            response.putAll(canvasTaskService.billingFields(taskId));
            return response;
        } catch (Exception error) {
            try {
                ossService.getOssClient().deleteObject(appProperties.getOss().getResultBucket(), objectName);
            } catch (Exception ignored) {
                // Best-effort cleanup; the original error is more useful to the caller.
            }
            if (error instanceof BusinessException businessException) throw businessException;
            throw new IllegalStateException("KIE 抠图任务提交失败：" + error.getMessage(), error);
        }
    }

    @Transactional
    public Map<String, Object> cutoutResult(Long projectId,
                                            Long userId,
                                            String operator,
                                            String shopName,
                                            String taskId) {
        requireOwnedProject(projectId, userId);
        String canvasId = templateCanvasId(projectId);
        canvasTaskService.requireOwnedTask(taskId, operator, shopName, canvasId);

        KieTaskResult result = canvasTaskService.findResult(taskId)
                .orElseThrow(() -> new BusinessException("抠图任务不存在"));
        if (!result.isFinished()) {
            KieTaskResult providerResult = kieClientService.getFullResult(taskId);
            canvasTaskService.recordPolledResult(providerResult);
            result = canvasTaskService.findResult(taskId).orElse(providerResult);
        }
        if (result.isFinished() && result.isSuccess()) {
            result = canvasTaskService.ensureResultPersisted(taskId).orElse(result);
        }

        String status = normalizeCutoutStatus(result);
        String servingUrl = result.isSuccess() ? canvasTaskService.resultServingUrl(result) : null;
        boolean storagePending = result.isSuccess() && (servingUrl == null || servingUrl.isBlank());
        if (result.isFinished()) cleanupCutoutInput(taskId, operator, shopName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", taskId);
        response.put("status", storagePending ? "processing" : status);
        response.put("result_url", servingUrl == null ? "" : servingUrl);
        response.put("error", result.getErrorMessage() == null ? "" : result.getErrorMessage());
        response.put("terminal", result.isFinished() && !storagePending);
        response.put("storage_pending", storagePending);
        response.putAll(canvasTaskService.billingFields(taskId));
        return response;
    }

    private String templateCanvasId(Long projectId) {
        return "template-lab:" + projectId;
    }

    private String normalizeCutoutStatus(KieTaskResult result) {
        if (result == null || !result.isFinished()) return "processing";
        return result.isSuccess() ? "success" : "failed";
    }

    @SuppressWarnings("unchecked")
    private void cleanupCutoutInput(String taskId, String operator, String shopName) {
        canvasTaskService.retryPayload(taskId, operator, shopName).ifPresent(snapshot -> {
            Object rawPayload = snapshot.get("payload");
            if (!(rawPayload instanceof Map<?, ?> payload)) return;
            Object rawObjectName = payload.get("provider_input_object");
            String objectName = rawObjectName == null ? "" : String.valueOf(rawObjectName).trim();
            if (objectName.isBlank()) return;
            try {
                ossService.getOssClient().deleteObject(appProperties.getOss().getResultBucket(), objectName);
            } catch (Exception ignored) {
                // The scheduled/result retry path may call this more than once.
            }
        });
    }

    TemplateLabProject requireOwnedProject(Long id, Long userId) {
        return projectRepository.findByIdAndOwnerUserId(id, userId)
                .orElseThrow(() -> new BusinessException("拼图项目不存在或无权访问"));
    }

    private boolean isAdmin(Long userId) {
        List<Long> admins = appProperties.getTemplateLabAdminUserIds();
        return admins != null && admins.contains(userId);
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
                    .findById(personalId)
                    .orElseThrow(() -> new BusinessException("个人模板不存在"));
            return personalTemplateNode(personal, userId);
        }
        JsonNode template = templateIndex.get(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在: " + templateId);
        }
        return template;
    }

    private JsonNode personalTemplateNode(TemplateLabPersonalTemplate template, Long userId) {
        ObjectNode definition = parseTemplateDefinition(template.getDefinitionJson());
        definition.put("id", "personal:" + template.getId());
        definition.put("source", "personal");
        definition.put("personalTemplateId", template.getId());
        definition.put("authorId", template.getOwnerUserId());
        definition.put("canDelete", template.getOwnerUserId() != null
                && (template.getOwnerUserId().equals(userId) || isAdmin(userId)));
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
        JsonNode images = definition.path("images");
        JsonNode texts = definition.path("texts");
        if (width < 320 || height < 320 || width > 8000 || height > 8000) {
            throw new BusinessException("模板画布尺寸必须在 320 到 8000 像素之间");
        }
        boolean hasFrames = frames.isArray() && !frames.isEmpty();
        boolean hasImages = images.isArray() && !images.isEmpty();
        if (!hasFrames && !hasImages) {
            throw new BusinessException("模板必须包含 1 到 20 个相框或图片位");
        }
        if (frames.isArray() && frames.size() > 20) {
            throw new BusinessException("模板相框不能超过 20 个");
        }
        if (images.isArray() && images.size() > 20) {
            throw new BusinessException("模板图片位不能超过 20 个");
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
        for (JsonNode image : images) {
            if (image.path("id").asText().isBlank()
                    || image.path("baseWidth").asDouble() <= 0
                    || image.path("baseHeight").asDouble() <= 0) {
                throw new BusinessException("模板包含无效图片位");
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

    private void validateCutoutInput(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("抠图输入不能为空");
        if (file.getSize() > MAX_CUTOUT_INPUT_BYTES) {
            throw new BusinessException("抠图临时图片不能超过 5MB");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(type)) {
            throw new BusinessException("抠图仅支持 JPG、PNG 和 WebP");
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

    private record ParsedCanvasSpec(String key, String usageType, String ratioGroup, int width, int height) {
    }

    private record StoredTemplateSource(String servingUrl, Path localPath, String ossObjectName) {
    }
}
