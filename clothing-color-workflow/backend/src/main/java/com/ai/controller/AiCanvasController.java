package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.dto.ApiResponse;
import com.ai.dto.CanvasProjectResponse;
import com.ai.dto.CanvasProjectSaveRequest;
import com.ai.dto.CanvasTemplateResponse;
import com.ai.dto.CanvasTemplateSaveRequest;
import com.ai.dto.KieTaskResult;
import com.ai.entity.CanvasProject;
import com.ai.entity.CanvasTemplate;
import com.ai.repository.CanvasProjectRepository;
import com.ai.repository.CanvasTemplateRepository;
import com.ai.service.CanvasTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import com.ai.service.impl.KieGptModels;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/canvas")
@RequiredArgsConstructor
@Slf4j
public class AiCanvasController {

    private static final String PROJECT_IMAGE_MODEL = "nano-banana-pro";
    private static final String PROJECT_VIDEO_MODEL = "sora-2";

    private final CanvasProjectRepository canvasProjectRepository;
    private final CanvasTemplateRepository canvasTemplateRepository;
    private final CanvasTaskService canvasTaskService;
    private final KieClientService kieClientService;
    private final OssService ossService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    @GetMapping("/projects")
    public ApiResponse<List<CanvasProjectResponse>> listProjects(HttpServletRequest request) {
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        List<CanvasProjectResponse> responses = canvasProjectRepository
                .findByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator)
                .stream()
                .map(project -> CanvasProjectResponse.from(project, objectMapper, false))
                .toList();
        return ApiResponse.ok("ok", responses);
    }

    @GetMapping("/projects/latest")
    public ApiResponse<CanvasProjectResponse> latestProject(HttpServletRequest request) {
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        return canvasProjectRepository.findTopByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator)
                .map(project -> ApiResponse.ok("ok", CanvasProjectResponse.from(project, objectMapper, true)))
                .orElseGet(() -> ApiResponse.ok("empty", null));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<CanvasProjectResponse> getProject(@PathVariable Long id, HttpServletRequest request) {
        CanvasProject project = loadOwnedProject(id, request);
        return ApiResponse.ok("ok", CanvasProjectResponse.from(project, objectMapper, true));
    }

    @PostMapping("/projects/autosave")
    public ApiResponse<CanvasProjectResponse> autosaveProject(@RequestBody CanvasProjectSaveRequest saveRequest,
                                                              HttpServletRequest request) {
        CanvasProject project = resolveProjectForSave(saveRequest, request);
        applyProjectPayload(project, saveRequest, request);
        CanvasProject saved = canvasProjectRepository.save(project);
        return ApiResponse.ok("saved", CanvasProjectResponse.from(saved, objectMapper, false));
    }

    @PostMapping("/projects")
    public ApiResponse<CanvasProjectResponse> saveProject(@RequestBody CanvasProjectSaveRequest saveRequest,
                                                          HttpServletRequest request) {
        CanvasProject project = resolveProjectForSave(saveRequest, request);
        applyProjectPayload(project, saveRequest, request);
        CanvasProject saved = canvasProjectRepository.save(project);
        return ApiResponse.ok("saved", CanvasProjectResponse.from(saved, objectMapper, true));
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<String> deleteProject(@PathVariable Long id, HttpServletRequest request) {
        CanvasProject project = loadOwnedProject(id, request);
        canvasProjectRepository.delete(project);
        return ApiResponse.ok("deleted", null);
    }

    @GetMapping("/templates")
    public ApiResponse<List<CanvasTemplateResponse>> listTemplates(HttpServletRequest request) {
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        List<CanvasTemplateResponse> responses = canvasTemplateRepository
                .findByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator)
                .stream()
                .map(template -> CanvasTemplateResponse.from(template, objectMapper, false))
                .toList();
        return ApiResponse.ok("ok", responses);
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<CanvasTemplateResponse> getTemplate(@PathVariable Long id, HttpServletRequest request) {
        CanvasTemplate template = loadOwnedTemplate(id, request);
        return ApiResponse.ok("ok", CanvasTemplateResponse.from(template, objectMapper, true));
    }

    @PostMapping("/templates")
    public ApiResponse<CanvasTemplateResponse> saveTemplate(@RequestBody CanvasTemplateSaveRequest saveRequest,
                                                            HttpServletRequest request) {
        CanvasTemplate template = resolveTemplateForSave(saveRequest, request);
        applyTemplatePayload(template, saveRequest, request);
        CanvasTemplate saved = canvasTemplateRepository.save(template);
        return ApiResponse.ok("saved", CanvasTemplateResponse.from(saved, objectMapper, true));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<String> deleteTemplate(@PathVariable Long id, HttpServletRequest request) {
        CanvasTemplate template = loadOwnedTemplate(id, request);
        canvasTemplateRepository.delete(template);
        return ApiResponse.ok("deleted", null);
    }

    @GetMapping("/kie/v1/models")
    public Map<String, Object> listKieModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        models.add(Map.of("id", KieGptModels.GPT_5_6_SOL, "object", "model", "owned_by", "ai-project"));
        models.add(Map.of("id", KieGptModels.GPT_5_6_TERRA, "object", "model", "owned_by", "ai-project"));
        models.add(Map.of("id", KieGptModels.GPT_5_6_LUNA, "object", "model", "owned_by", "ai-project"));
        models.add(Map.of("id", "project-image", "object", "model", "owned_by", "ai-project"));
        models.add(Map.of("id", "project-video", "object", "model", "owned_by", "ai-project"));
        return Map.of("object", "list", "data", models);
    }

    @PostMapping("/kie/v1/chat/completions")
    public Map<String, Object> chatCompletions(@RequestBody Map<String, Object> payload) {
        String model = KieGptModels.normalizeTextModel(textValue(payload.get("model")));
        String text = callKieText(payload, model);
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "assistant");
        message.put("content", text);

        ObjectNode choice = objectMapper.createObjectNode();
        choice.put("index", 0);
        choice.set("message", message);
        choice.put("finish_reason", "stop");

        ArrayNode choices = objectMapper.createArrayNode();
        choices.add(choice);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "chatcmpl-canvas-" + UUID.randomUUID());
        root.put("object", "chat.completion");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model);
        root.set("choices", choices);
        return objectMapper.convertValue(root, new TypeReference<Map<String, Object>>() {});
    }

    @PostMapping("/kie/v1/images/generations")
    public Map<String, Object> createImage(@RequestBody Map<String, Object> payload,
                                           HttpServletRequest request) {
        String prompt = textValue(payload.get("prompt"));
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        String model = normalizeModel(textValue(payload.get("model")), PROJECT_IMAGE_MODEL);
        String resolution = firstNonBlank(textValue(payload.get("resolution")), resolutionFromSize(textValue(payload.get("size"))), "2K");
        String aspectRatio = firstNonBlank(textValue(payload.get("aspect_ratio")), textValue(payload.get("ratio")), "auto");
        List<String> images = normalizeImageInputs(payload);
        String inputUrl = images.isEmpty() ? "" : images.get(0);
        String colorUrl = images.size() > 1 ? String.join(",", images.subList(1, images.size())) : "";

        String taskId = kieClientService.createTask(
                "AI_CANVAS",
                prompt,
                resolution,
                aspectRatio,
                model,
                inputUrl,
                colorUrl,
                appProperties.getKie().getCallbackUrl()
        );
        canvasTaskService.recordCreated(taskId, "image", currentOperator(request), currentShopName(request));
        return Map.of(
                "task_id", taskId,
                "taskId", taskId,
                "data", Map.of("taskId", taskId)
        );
    }

    @GetMapping("/kie/v1/images/tasks/{taskId}")
    public Map<String, Object> getImageTask(@PathVariable String taskId) {
        return toAsyncTaskResponse(taskId, "image");
    }

    @PostMapping("/kie/v1/videos/generations")
    public Map<String, Object> createVideo(@RequestBody Map<String, Object> payload,
                                           HttpServletRequest request) {
        String model = normalizeModel(textValue(payload.get("model")), PROJECT_VIDEO_MODEL);
        Map<String, Object> input = new LinkedHashMap<>(payload);
        input.remove("model");
        if (!input.containsKey("prompt") || textValue(input.get("prompt")).isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        List<String> images = normalizeImageInputs(payload);
        if (!images.isEmpty()) {
            input.put("image_input", images);
        }
        KieTaskResult result = kieClientService.createVideoTask(model, input);
        String taskId = result.getTaskId();
        canvasTaskService.recordCreated(taskId, "video", currentOperator(request), currentShopName(request));
        return Map.of(
                "task_id", taskId,
                "taskId", taskId,
                "data", Map.of("id", taskId, "task_id", taskId)
        );
    }

    @PostMapping("/callback")
    public ApiResponse<String> canvasCallback(@RequestBody Map<String, Object> payload) {
        boolean handled = canvasTaskService.refreshTaskByCallback(payload);
        return ApiResponse.ok(handled ? "ok" : "task not found", null);
    }

    @GetMapping({"/kie/v1/videos/generations/{taskId}", "/kie/v2/videos/generations/{taskId}", "/kie/v1/videos/tasks/{taskId}"})
    public Map<String, Object> getVideoTask(@PathVariable String taskId) {
        Map<String, Object> task = toAsyncTaskResponse(taskId, "video");
        String status = textValue(task.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) task.getOrDefault("data", Map.of());
        String url = "";
        Object outputsObj = data.get("outputs");
        if (outputsObj instanceof List<?> outputs && !outputs.isEmpty() && outputs.get(0) instanceof Map<?, ?> first) {
            url = textValue(first.get("url"));
        }
        return Map.of(
                "id", taskId,
                "task_id", taskId,
                "status", "SUCCESS".equalsIgnoreCase(status) ? "succeeded" : ("FAILED".equalsIgnoreCase(status) ? "failed" : "processing"),
                "data", Map.of("id", taskId, "output", url, "video_url", url, "url", url),
                "output", url,
                "video_url", url
        );
    }

    private Map<String, Object> toAsyncTaskResponse(String taskId, String mediaType) {
        Optional<KieTaskResult> storedResult = canvasTaskService.findResult(taskId);
        if (storedResult.isPresent()) {
            KieTaskResult result = storedResult.get();
            if (result.isFinished() || isCallbackMode()) {
                return toAsyncTaskResponse(taskId, mediaType, result);
            }
        }

        KieTaskResult result = kieClientService.getFullResult(taskId);
        canvasTaskService.recordPolledResult(result);
        return toAsyncTaskResponse(taskId, mediaType, result);
    }

    private Map<String, Object> toAsyncTaskResponse(String taskId, String mediaType, KieTaskResult result) {
        String status = result.getStatus();
        if (status == null || status.isBlank()) {
            status = result.isFinished() ? (result.isSuccess() ? "SUCCESS" : "FAILED") : "PROCESSING";
        }
        List<Map<String, String>> outputs = new ArrayList<>();
        if ("SUCCESS".equalsIgnoreCase(status) && result.getResultUrl() != null && !result.getResultUrl().isBlank()) {
            outputs.add(Map.of("url", result.getResultUrl(), "type", mediaType));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        data.put("outputs", outputs);
        data.put("url", outputs.isEmpty() ? "" : outputs.get(0).get("url"));
        data.put("error", result.getErrorMessage());
        return Map.of(
                "taskId", taskId,
                "task_id", taskId,
                "status", status.toUpperCase(),
                "data", data,
                "message", result.getErrorMessage() == null ? "" : result.getErrorMessage()
        );
    }

    private boolean isCallbackMode() {
        String callbackUrl = appProperties.getKie().getCallbackUrl();
        return callbackUrl != null && !callbackUrl.isBlank();
    }

    private String callKieText(Map<String, Object> payload, String model) {
        try {
            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", KieGptModels.normalizeTextModel(model));
            rootNode.put("stream", false);
            ObjectNode reasoning = objectMapper.createObjectNode();
            reasoning.put("effort", "medium");
            rootNode.set("reasoning", reasoning);
            rootNode.set("input", convertMessagesToResponsesInput(payload.get("messages")));

            String jsonBody = objectMapper.writeValueAsString(rootNode);
            Request request = new Request.Builder()
                    .url(KieGptModels.RESPONSES_API_URL)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(okhttp3.RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new RuntimeException("KIE 文本模型调用失败: HTTP " + response.code() + " " + responseBody);
                }
                return parseTextFromKieResponse(responseBody);
            }
        } catch (Exception e) {
            log.error("[AI Canvas] text proxy failed", e);
            throw new RuntimeException("文本模型调用失败: " + e.getMessage(), e);
        }
    }

    private ArrayNode convertMessagesToResponsesInput(Object messagesObj) {
        ArrayNode input = objectMapper.createArrayNode();
        if (!(messagesObj instanceof List<?> messages)) {
            ObjectNode user = objectMapper.createObjectNode();
            user.put("role", "user");
            ArrayNode content = objectMapper.createArrayNode();
            ObjectNode text = objectMapper.createObjectNode();
            text.put("type", "input_text");
            text.put("text", textValue(messagesObj));
            content.add(text);
            user.set("content", content);
            input.add(user);
            return input;
        }
        for (Object messageObj : messages) {
            if (!(messageObj instanceof Map<?, ?> messageMap)) continue;
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", firstNonBlank(textValue(messageMap.get("role")), "user"));
            ArrayNode content = objectMapper.createArrayNode();
            Object rawContent = messageMap.get("content");
            if (rawContent instanceof List<?> parts) {
                for (Object partObj : parts) {
                    if (!(partObj instanceof Map<?, ?> partMap)) continue;
                    String type = textValue(partMap.get("type"));
                    if ("image_url".equals(type)) {
                        Object imageObj = partMap.get("image_url");
                        String imageUrl = imageObj instanceof Map<?, ?> imageMap
                                ? textValue(imageMap.get("url"))
                                : textValue(imageObj);
                        if (!imageUrl.isBlank()) {
                            ObjectNode image = objectMapper.createObjectNode();
                            image.put("type", "input_image");
                            image.put("image_url", imageUrl);
                            content.add(image);
                        }
                    } else {
                        String text = firstNonBlank(textValue(partMap.get("text")), textValue(partMap.get("content")));
                        if (!text.isBlank()) {
                            ObjectNode textNode = objectMapper.createObjectNode();
                            textNode.put("type", "input_text");
                            textNode.put("text", text);
                            content.add(textNode);
                        }
                    }
                }
            } else {
                ObjectNode textNode = objectMapper.createObjectNode();
                textNode.put("type", "input_text");
                textNode.put("text", textValue(rawContent));
                content.add(textNode);
            }
            message.set("content", content);
            input.add(message);
        }
        return input;
    }

    private String parseTextFromKieResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.hasNonNull("output_text")) return root.get("output_text").asText();
        StringBuilder sb = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode part : content) {
                        if (part.hasNonNull("text")) {
                            if (!sb.isEmpty()) sb.append('\n');
                            sb.append(part.get("text").asText());
                        }
                    }
                }
            }
        }
        if (!sb.isEmpty()) return sb.toString();
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (!content.isMissingNode()) return content.asText();
        }
        return responseBody;
    }

    private CanvasProject resolveProjectForSave(CanvasProjectSaveRequest saveRequest, HttpServletRequest request) {
        if (saveRequest.getId() != null) {
            return loadOwnedProject(saveRequest.getId(), request);
        }
        CanvasProject project = new CanvasProject();
        project.setOperator(currentOperator(request));
        project.setShopName(currentShopName(request));
        return project;
    }

    private void applyProjectPayload(CanvasProject project, CanvasProjectSaveRequest saveRequest, HttpServletRequest request) {
        project.setOperator(currentOperator(request));
        project.setShopName(currentShopName(request));
        String projectName = saveRequest.getProjectName();
        if (projectName == null || projectName.isBlank()) {
            projectName = "AI 画布 " + Instant.now();
        }
        project.setProjectName(projectName.trim());
        try {
            project.setSnapshotJson(objectMapper.writeValueAsString(saveRequest.getSnapshot() == null ? Map.of() : saveRequest.getSnapshot()));
            project.setMetaJson(objectMapper.writeValueAsString(saveRequest.getMeta() == null ? Map.of() : saveRequest.getMeta()));
        } catch (Exception e) {
            throw new RuntimeException("画布快照序列化失败", e);
        }
    }

    private CanvasProject loadOwnedProject(Long id, HttpServletRequest request) {
        CanvasProject project = canvasProjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("画布记录不存在"));
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        if (!operator.equals(project.getOperator()) || !shopName.equals(project.getShopName())) {
            throw new RuntimeException("无权访问该画布记录");
        }
        return project;
    }

    private CanvasTemplate resolveTemplateForSave(CanvasTemplateSaveRequest saveRequest, HttpServletRequest request) {
        if (saveRequest.getId() != null) {
            return loadOwnedTemplate(saveRequest.getId(), request);
        }
        CanvasTemplate template = new CanvasTemplate();
        template.setOperator(currentOperator(request));
        template.setShopName(currentShopName(request));
        return template;
    }

    private void applyTemplatePayload(CanvasTemplate template, CanvasTemplateSaveRequest saveRequest, HttpServletRequest request) {
        template.setOperator(currentOperator(request));
        template.setShopName(currentShopName(request));
        String templateName = saveRequest.getTemplateName();
        if (templateName == null || templateName.isBlank()) {
            templateName = "画布模板 " + Instant.now();
        }
        template.setTemplateName(templateName.trim());
        template.setCategory(firstNonBlank(saveRequest.getCategory(), "未分类"));
        template.setCoverImageUrl(firstNonBlank(saveRequest.getCoverImageUrl()));
        template.setDescription(firstNonBlank(saveRequest.getDescription()));
        try {
            template.setTagsJson(objectMapper.writeValueAsString(saveRequest.getTags() == null ? List.of() : saveRequest.getTags()));
            template.setSnapshotJson(objectMapper.writeValueAsString(saveRequest.getSnapshot() == null ? Map.of() : saveRequest.getSnapshot()));
            template.setMetaJson(objectMapper.writeValueAsString(saveRequest.getMeta() == null ? Map.of() : saveRequest.getMeta()));
        } catch (Exception e) {
            throw new RuntimeException("画布模板序列化失败", e);
        }
    }

    private CanvasTemplate loadOwnedTemplate(Long id, HttpServletRequest request) {
        CanvasTemplate template = canvasTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("画布模板不存在"));
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        if (!operator.equals(template.getOperator()) || !shopName.equals(template.getShopName())) {
            throw new RuntimeException("无权访问该画布模板");
        }
        return template;
    }

    private List<String> normalizeImageInputs(Map<String, Object> payload) {
        List<String> raw = new ArrayList<>();
        collectImageCandidate(raw, payload.get("imageUrls"));
        collectImageCandidate(raw, payload.get("imagesUrl"));
        collectImageCandidate(raw, payload.get("imagesUrls"));
        collectImageCandidate(raw, payload.get("imageUrl"));
        collectImageCandidate(raw, payload.get("image_url"));
        collectImageCandidate(raw, payload.get("input_image"));
        collectImageCandidate(raw, payload.get("image_input"));
        collectImageCandidate(raw, payload.get("firstFrameUrl"));
        collectImageCandidate(raw, payload.get("lastFrameUrl"));

        List<String> urls = new ArrayList<>();
        for (String item : raw) {
            String normalized = normalizeImageUrl(item);
            if (!normalized.isBlank() && !urls.contains(normalized)) {
                urls.add(normalized);
            }
        }
        return urls;
    }

    private void collectImageCandidate(List<String> target, Object value) {
        if (value == null) return;
        if (value instanceof List<?> list) {
            for (Object item : list) collectImageCandidate(target, item);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            collectImageCandidate(target, map.get("url"));
            collectImageCandidate(target, map.get("image_url"));
            collectImageCandidate(target, map.get("imageUrl"));
            return;
        }
        String text = textValue(value);
        if (!text.isBlank() && !"null".equalsIgnoreCase(text) && !"undefined".equalsIgnoreCase(text)) {
            target.add(text);
        }
    }

    private String normalizeImageUrl(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) return "";
        if (!raw.startsWith("data:")) return raw;
        return uploadDataUrl(raw);
    }

    private String uploadDataUrl(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return dataUrl;
            String header = dataUrl.substring(0, comma);
            String base64 = dataUrl.substring(comma + 1);
            if (base64.contains("%")) {
                base64 = URLDecoder.decode(base64, StandardCharsets.UTF_8);
            }
            byte[] bytes = Base64.getDecoder().decode(base64);
            String ext = ".png";
            String contentType = "image/png";
            if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                ext = ".jpg";
                contentType = "image/jpeg";
            } else if (header.contains("image/webp")) {
                ext = ".webp";
                contentType = "image/webp";
            }
            String objectName = "AI_CANVAS/input/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;
            com.aliyun.oss.model.ObjectMetadata metadata = new com.aliyun.oss.model.ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            ossService.getOssClient().putObject(
                    appProperties.getOss().getInputBucket(),
                    objectName,
                    new java.io.ByteArrayInputStream(bytes),
                    metadata
            );
            return appProperties.getOss().getInputPublicHost() + "/" + objectName;
        } catch (Exception e) {
            log.warn("[AI Canvas] data URL 上传 OSS 失败，回退原始内容: {}", e.getMessage());
            return dataUrl;
        }
    }

    private String normalizeModel(String model, String fallback) {
        if (model == null || model.isBlank() || model.startsWith("project-")) {
            return fallback;
        }
        return model;
    }

    private String resolutionFromSize(String size) {
        if (size == null || size.isBlank()) return "";
        String upper = size.toUpperCase();
        if (upper.contains("4096") || upper.contains("4K")) return "4K";
        if (upper.contains("2048") || upper.contains("2K")) return "2K";
        if (upper.contains("1024") || upper.contains("1K")) return "1K";
        return "";
    }

    private String currentOperator(HttpServletRequest request) {
        Object value = request.getAttribute("operator");
        return value == null ? "unknown" : value.toString();
    }

    private String currentShopName(HttpServletRequest request) {
        Object value = request.getAttribute("shopName");
        return value == null ? "unknown" : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String textValue(Object value) {
        if (value == null) return "";
        if (value instanceof String str) return str;
        return String.valueOf(value);
    }
}
