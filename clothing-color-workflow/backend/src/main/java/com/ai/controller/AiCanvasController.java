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

import java.io.File;
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
    private static final List<String> PROJECT_IMAGE_MODELS = List.of(PROJECT_IMAGE_MODEL, "gpt-image-2-image-to-image");
    private static final String SEEDANCE_2_5_MODEL = "bytedance/seedance-2-5";
    private static final String SEEDANCE_2_MODEL = "bytedance/seedance-2";
    private static final String MINIMAX_H3_TEXT_MODEL = "minimax-h3/text-to-video";
    private static final String MINIMAX_H3_IMAGE_MODEL = "minimax-h3/image-to-video";
    private static final String MINIMAX_H3_REFERENCE_MODEL = "minimax-h3/reference-to-video";
    private static final List<String> PROJECT_VIDEO_MODELS = List.of(
            SEEDANCE_2_5_MODEL,
            SEEDANCE_2_MODEL,
            MINIMAX_H3_TEXT_MODEL,
            MINIMAX_H3_IMAGE_MODEL,
            MINIMAX_H3_REFERENCE_MODEL
    );
    private static final String PROJECT_VIDEO_MODEL = SEEDANCE_2_5_MODEL;

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
                .filter(project -> !isInfiniteCanvasInternalProject(project))
                .map(project -> CanvasProjectResponse.from(project, objectMapper, false))
                .toList();
        return ApiResponse.ok("ok", responses);
    }

    @GetMapping("/projects/latest")
    public ApiResponse<CanvasProjectResponse> latestProject(HttpServletRequest request) {
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        return canvasProjectRepository.findByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator)
                .stream()
                .filter(project -> !isInfiniteCanvasInternalProject(project))
                .findFirst()
                .map(project -> ApiResponse.ok("ok", CanvasProjectResponse.from(project, objectMapper, true)))
                .orElseGet(() -> ApiResponse.ok("empty", null));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<CanvasProjectResponse> getProject(@PathVariable("id") Long id, HttpServletRequest request) {
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
    public ApiResponse<String> deleteProject(@PathVariable("id") Long id, HttpServletRequest request) {
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
    public ApiResponse<CanvasTemplateResponse> getTemplate(@PathVariable("id") Long id, HttpServletRequest request) {
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
    public ApiResponse<String> deleteTemplate(@PathVariable("id") Long id, HttpServletRequest request) {
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
        PROJECT_IMAGE_MODELS.forEach(model -> models.add(Map.of("id", model, "object", "model", "owned_by", "ai-project")));
        PROJECT_VIDEO_MODELS.forEach(model -> models.add(Map.of("id", model, "object", "model", "owned_by", "ai-project")));
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
    public Map<String, Object> getImageTask(@PathVariable("taskId") String taskId) {
        return toAsyncTaskResponse(taskId, "image");
    }

    @PostMapping("/kie/v1/videos/generations")
    public Map<String, Object> createVideo(@RequestBody Map<String, Object> payload,
                                           HttpServletRequest request) {
        String model = normalizeVideoModel(textValue(payload.get("model")));
        Map<String, Object> input = videoInput(payload, model);
        if (!input.containsKey("prompt") || textValue(input.get("prompt")).isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
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
    public Map<String, Object> getVideoTask(@PathVariable("taskId") String taskId) {
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
        // 重新读取已落本地的结果（含 localPath），保证返回本地 /ai-result URL
        KieTaskResult stored = canvasTaskService.findResult(taskId).orElse(result);
        return toAsyncTaskResponse(taskId, mediaType, stored);
    }

    private Map<String, Object> toAsyncTaskResponse(String taskId, String mediaType, KieTaskResult result) {
        String status = result.getStatus();
        if (status == null || status.isBlank()) {
            status = result.isFinished() ? (result.isSuccess() ? "SUCCESS" : "FAILED") : "PROCESSING";
        }
        List<Map<String, String>> outputs = new ArrayList<>();
        if ("SUCCESS".equalsIgnoreCase(status) && result.getResultUrl() != null && !result.getResultUrl().isBlank()) {
            String url = result.getResultUrl();
            // 🔴 优先返回本地落盘后的 /ai-result/... 地址（仅本地，不走 OSS）
            String localUrl = result.getLocalPath() != null ? ossService.localServingUrl(result.getLocalPath()) : null;
            if (localUrl != null && result.getLocalPath() != null && new File(result.getLocalPath()).exists()) {
                url = localUrl;
            }
            outputs.add(Map.of("url", url, "type", mediaType));
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

    private boolean isInfiniteCanvasInternalProject(CanvasProject project) {
        if (project == null) return false;
        String projectName = project.getProjectName();
        if ("__infinite_canvas_workspace__".equals(projectName) || "__infinite_canvas_library__".equals(projectName)) return true;
        String metaJson = project.getMetaJson();
        return metaJson != null && (metaJson.contains("\"kind\":\"infinite-canvas\"")
                || metaJson.contains("\"kind\":\"infinite-canvas-workspace\"")
                || metaJson.contains("\"kind\":\"infinite-canvas-library\""));
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
        collectImageCandidate(raw, payload.get("images"));
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

    private Map<String, Object> videoInput(Map<String, Object> payload, String model) {
        if (isMiniMaxH3VideoModel(model)) return miniMaxH3VideoInput(payload, model);
        return seedanceVideoInput(payload, model);
    }

    private Map<String, Object> seedanceVideoInput(Map<String, Object> payload, String model) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", textValue(payload.get("prompt")));
        input.put("duration", seedanceDuration(payload.get("duration"), model));
        input.put("aspect_ratio", seedanceAspectRatio(textValue(payload.get("aspect_ratio"))));
        input.put("resolution", seedanceResolution(textValue(payload.get("resolution"))));
        input.put("generate_audio", boolValue(payload.get("generate_audio")));
        input.put("return_last_frame", boolValue(payload.get("return_last_frame")));
        input.put("web_search", boolValue(payload.get("web_search")));
        if (SEEDANCE_2_5_MODEL.equals(model)) {
            input.put("output_format", "mov".equalsIgnoreCase(textValue(payload.get("output_format"))) ? "mov" : "mp4");
            input.put("nsfw_checker", !payload.containsKey("nsfw_checker") || boolValue(payload.get("nsfw_checker")));
        }

        List<String> firstFrames = mergeUrlInputs(payload.get("first_frame_url"), payload.get("firstFrameUrl"));
        List<String> lastFrames = mergeUrlInputs(payload.get("last_frame_url"), payload.get("lastFrameUrl"));
        List<String> referenceImages = normalizeUrlInputs(payload.get("reference_image_urls"));
        List<String> referenceVideos = mergeUrlInputs(payload.get("reference_video_urls"), payload.get("videos"));
        List<String> referenceAudios = mergeUrlInputs(payload.get("reference_audio_urls"), payload.get("audios"));
        List<String> images = normalizeImageInputs(payload);
        boolean useMultimodal = !referenceImages.isEmpty() || !referenceVideos.isEmpty() || !referenceAudios.isEmpty()
                || boolValue(payload.get("multimodal"));

        if (useMultimodal || (images.size() > 1 && firstFrames.isEmpty() && lastFrames.isEmpty())) {
            List<String> refs = !referenceImages.isEmpty() ? referenceImages : images;
            if (!refs.isEmpty()) input.put("reference_image_urls", refs);
            if (!referenceVideos.isEmpty()) input.put("reference_video_urls", referenceVideos);
            if (!referenceAudios.isEmpty()) input.put("reference_audio_urls", referenceAudios);
        } else if (!firstFrames.isEmpty() && !lastFrames.isEmpty()) {
            input.put("first_frame_url", firstFrames.get(0));
            input.put("last_frame_url", lastFrames.get(0));
        } else if (!firstFrames.isEmpty()) {
            input.put("first_frame_url", firstFrames.get(0));
        } else if (!images.isEmpty()) {
            input.put("first_frame_url", images.get(0));
        }
        return input;
    }

    private Map<String, Object> miniMaxH3VideoInput(Map<String, Object> payload, String model) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", textValue(payload.get("prompt")));
        input.put("duration", miniMaxH3Duration(payload.get("duration")));

        List<String> firstFrames = mergeUrlInputs(payload.get("first_frame_url"), payload.get("firstFrameUrl"));
        List<String> lastFrames = mergeUrlInputs(payload.get("last_frame_url"), payload.get("lastFrameUrl"));
        List<String> referenceImages = normalizeUrlInputs(payload.get("reference_image_urls"));
        List<String> referenceVideos = mergeUrlInputs(payload.get("reference_video_urls"), payload.get("videos"));
        List<String> referenceAudios = mergeUrlInputs(payload.get("reference_audio_urls"), payload.get("audios"));
        List<String> images = normalizeImageInputs(payload);

        if (MINIMAX_H3_TEXT_MODEL.equals(model)) {
            input.put("aspect_ratio", miniMaxH3AspectRatio(textValue(payload.get("aspect_ratio")), false));
            return input;
        }

        if (MINIMAX_H3_IMAGE_MODEL.equals(model)) {
            String firstFrame = !firstFrames.isEmpty() ? firstFrames.get(0) : (images.isEmpty() ? "" : images.get(0));
            if (firstFrame.isBlank()) throw new IllegalArgumentException("MiniMax H3 图生视频需要至少一张参考图片");
            input.put("first_frame_url", firstFrame);
            String lastFrame = !lastFrames.isEmpty() ? lastFrames.get(0) : (images.size() > 1 ? images.get(1) : "");
            if (!lastFrame.isBlank() && !lastFrame.equals(firstFrame)) input.put("last_frame_url", lastFrame);
            return input;
        }

        List<String> references = !referenceImages.isEmpty() ? referenceImages : images;
        if (references.isEmpty() && referenceVideos.isEmpty() && referenceAudios.isEmpty()) {
            throw new IllegalArgumentException("MiniMax H3 多模态参考需要至少一项图片、视频或音频素材");
        }
        input.put("aspect_ratio", miniMaxH3AspectRatio(textValue(payload.get("aspect_ratio")), true));
        if (!references.isEmpty()) input.put("reference_image_urls", references);
        if (!referenceVideos.isEmpty()) input.put("reference_video_urls", referenceVideos);
        if (!referenceAudios.isEmpty()) input.put("reference_audio_urls", referenceAudios);
        return input;
    }

    private boolean isMiniMaxH3VideoModel(String model) {
        return MINIMAX_H3_TEXT_MODEL.equals(model)
                || MINIMAX_H3_IMAGE_MODEL.equals(model)
                || MINIMAX_H3_REFERENCE_MODEL.equals(model);
    }

    private List<String> mergeUrlInputs(Object... values) {
        List<String> urls = new ArrayList<>();
        for (Object value : values) {
            for (String item : normalizeUrlInputs(value)) {
                if (!urls.contains(item)) urls.add(item);
            }
        }
        return urls;
    }

    private List<String> normalizeUrlInputs(Object value) {
        List<String> raw = new ArrayList<>();
        collectImageCandidate(raw, value);
        List<String> urls = new ArrayList<>();
        for (String item : raw) {
            String normalized = normalizeImageUrl(item);
            if (!normalized.isBlank() && !urls.contains(normalized)) urls.add(normalized);
        }
        return urls;
    }

    private int seedanceDuration(Object value, String model) {
        int max = SEEDANCE_2_5_MODEL.equals(model) ? 30 : 15;
        try {
            return Math.max(1, Math.min(max, Integer.parseInt(textValue(value))));
        } catch (Exception ignored) {
            return 5;
        }
    }

    private int miniMaxH3Duration(Object value) {
        try {
            return Math.max(5, Math.min(15, Integer.parseInt(textValue(value))));
        } catch (Exception ignored) {
            return 6;
        }
    }

    private String miniMaxH3AspectRatio(String value, boolean referenceMode) {
        List<String> allowed = referenceMode
                ? List.of("16:9", "9:16", "1:1", "adaptive")
                : List.of("16:9", "9:16", "1:1");
        return allowed.contains(value) ? value : "16:9";
    }

    private String seedanceResolution(String value) {
        if ("1080p".equalsIgnoreCase(value)) return "1080p";
        return "480p".equalsIgnoreCase(value) ? "480p" : "720p";
    }

    private String seedanceAspectRatio(String value) {
        return List.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(value)
                ? value
                : "16:9";
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
        return PROJECT_IMAGE_MODELS.contains(model.trim()) ? model.trim() : fallback;
    }

    private String normalizeVideoModel(String model) {
        if (model == null || model.isBlank() || model.startsWith("project-")) return PROJECT_VIDEO_MODEL;
        return PROJECT_VIDEO_MODELS.contains(model.trim()) ? model.trim() : PROJECT_VIDEO_MODEL;
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

    private boolean boolValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        String text = textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }
}
