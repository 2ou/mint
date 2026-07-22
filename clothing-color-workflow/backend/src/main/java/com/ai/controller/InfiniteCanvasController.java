package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.entity.CanvasProject;
import com.ai.repository.CanvasProjectRepository;
import com.ai.service.CanvasTaskService;
import com.ai.service.KieClientService;
import com.ai.service.OssService;
import com.ai.service.TextModelService;
import com.ai.service.impl.KieGptModels;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class InfiniteCanvasController {

    private static final String WORKSPACE_KIND = "infinite-canvas-workspace";
    private static final String CANVAS_KIND = "infinite-canvas";
    private static final String WORKSPACE_PROJECT_NAME = "__infinite_canvas_workspace__";
    private static final String DEFAULT_PROJECT_ID = "default";
    private static final String PROJECT_IMAGE_MODEL = "nano-banana-pro";
    private static final String PROJECT_VIDEO_MODEL = "sora-2";

    private final CanvasProjectRepository canvasProjectRepository;
    private final CanvasTaskService canvasTaskService;
    private final KieClientService kieClientService;
    private final TextModelService textModelService;
    private final OssService ossService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @GetMapping("/projects")
    public Map<String, Object> listWorkspaceProjects(HttpServletRequest request) {
        List<CanvasProject> rows = ownedRows(request);
        return Map.of("projects", workspaceProjects(request, rows));
    }

    @PostMapping("/projects")
    public Map<String, Object> createWorkspaceProject(@RequestBody Map<String, Object> payload,
                                                      HttpServletRequest request) {
        CanvasProject row = workspaceRow(request);
        List<Map<String, Object>> projects = readWorkspaceProjects(row);
        int nextOrder = projects.stream()
                .mapToInt(item -> intValue(item.get("order"), 0))
                .max()
                .orElse(0) + 1;
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", "project_" + UUID.randomUUID().toString().replace("-", ""));
        project.put("name", firstNonBlank(textValue(payload.get("name")), "新项目"));
        project.put("order", nextOrder);
        project.put("updated_at", System.currentTimeMillis());
        projects.add(project);
        saveWorkspaceProjects(row, projects);
        return Map.of("project", decorateProject(project, activeCanvasRows(request), projects));
    }

    @PostMapping("/projects/{projectId}")
    public Map<String, Object> updateWorkspaceProject(@PathVariable("projectId") String projectId,
                                                      @RequestBody Map<String, Object> payload,
                                                      HttpServletRequest request) {
        CanvasProject row = workspaceRow(request);
        List<Map<String, Object>> projects = readWorkspaceProjects(row);
        Map<String, Object> target = projects.stream()
                .filter(item -> projectId.equals(textValue(item.get("id"))))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        if (payload.containsKey("name")) {
            target.put("name", firstNonBlank(textValue(payload.get("name")), textValue(target.get("name")), "未命名项目"));
        }
        if (payload.containsKey("order")) {
            target.put("order", intValue(payload.get("order"), intValue(target.get("order"), 0)));
        }
        target.put("updated_at", System.currentTimeMillis());
        saveWorkspaceProjects(row, projects);
        return Map.of("project", decorateProject(target, activeCanvasRows(request), projects));
    }

    @DeleteMapping("/projects/{projectId}")
    public Map<String, Object> deleteWorkspaceProject(@PathVariable("projectId") String projectId,
                                                      HttpServletRequest request) {
        if (DEFAULT_PROJECT_ID.equals(projectId)) {
            throw new RuntimeException("默认项目不可删除");
        }
        CanvasProject row = workspaceRow(request);
        List<Map<String, Object>> projects = readWorkspaceProjects(row);
        boolean existed = projects.removeIf(item -> projectId.equals(textValue(item.get("id"))));
        if (!existed) {
            throw new RuntimeException("项目不存在");
        }
        int moved = 0;
        for (CanvasProject canvasRow : activeCanvasRows(request)) {
            Map<String, Object> canvas = canvasData(canvasRow);
            if (projectId.equals(textValue(canvas.get("project")))) {
                canvas.put("project", DEFAULT_PROJECT_ID);
                saveCanvasData(canvasRow, canvas);
                moved += 1;
            }
        }
        saveWorkspaceProjects(row, projects);
        return Map.of("ok", true, "moved", moved);
    }

    @GetMapping("/canvases")
    public Map<String, Object> listCanvases(HttpServletRequest request) {
        List<Map<String, Object>> canvases = activeCanvasRows(request).stream()
                .map(row -> canvasRecord(row, false))
                .sorted(canvasComparator())
                .toList();
        return Map.of("canvases", canvases);
    }

    @GetMapping("/canvases/trash")
    public Map<String, Object> listTrashedCanvases(HttpServletRequest request) {
        List<Map<String, Object>> canvases = ownedRows(request).stream()
                .filter(this::isInfiniteCanvasRow)
                .filter(row -> boolValue(readMeta(row).get("deleted")))
                .map(row -> canvasRecord(row, false))
                .sorted(canvasComparator())
                .toList();
        return Map.of("canvases", canvases, "retention_days", 30);
    }

    @PostMapping("/canvases")
    public Map<String, Object> createCanvas(@RequestBody Map<String, Object> payload,
                                            HttpServletRequest request) {
        CanvasProject row = new CanvasProject();
        row.setOperator(currentOperator(request));
        row.setShopName(currentShopName(request));
        row.setProjectName(firstNonBlank(textValue(payload.get("title")), "未命名画布"));
        row.setMetaJson("{}");
        row.setSnapshotJson("{}");
        row = canvasProjectRepository.save(row);

        Map<String, Object> canvas = defaultCanvas(row);
        canvas.putAll(cleanCanvasPayload(payload));
        canvas.put("id", String.valueOf(row.getId()));
        canvas.put("title", firstNonBlank(textValue(canvas.get("title")), "未命名画布"));
        canvas.put("kind", normalizeCanvasKind(textValue(canvas.get("kind"))));
        canvas.put("project", firstNonBlank(textValue(canvas.get("project")), DEFAULT_PROJECT_ID));
        canvas.putIfAbsent("nodes", List.of());
        canvas.putIfAbsent("connections", List.of());
        canvas.putIfAbsent("viewport", Map.of("x", 0, "y", 0, "scale", 1));
        canvas.putIfAbsent("logs", List.of());
        saveCanvasData(row, canvas);
        return Map.of("canvas", canvasRecord(row, true));
    }

    @GetMapping("/canvases/{canvasId}")
    public Map<String, Object> getCanvas(@PathVariable("canvasId") String canvasId, HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        return Map.of("canvas", canvasRecord(row, true));
    }

    @GetMapping("/canvases/{canvasId}/meta")
    public Map<String, Object> getCanvasMeta(@PathVariable("canvasId") String canvasId, HttpServletRequest request) {
        return canvasRecord(ownedCanvasRow(canvasId, request), false);
    }

    @PostMapping("/canvases/{canvasId}/meta")
    public Map<String, Object> updateCanvasMeta(@PathVariable("canvasId") String canvasId,
                                                @RequestBody Map<String, Object> payload,
                                                HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        Map<String, Object> canvas = canvasData(row);
        for (String key : List.of("title", "icon", "kind", "project", "board_x", "board_y", "owner", "color", "pinned")) {
            if (payload.containsKey(key)) {
                canvas.put(key, payload.get(key));
            }
        }
        canvas.put("kind", normalizeCanvasKind(textValue(canvas.get("kind"))));
        canvas.put("title", firstNonBlank(textValue(canvas.get("title")), "未命名画布"));
        saveCanvasData(row, canvas);
        return Map.of("canvas", canvasRecord(row, false));
    }

    @PostMapping("/canvases/{canvasId}/touch")
    public Map<String, Object> touchCanvas(@PathVariable("canvasId") String canvasId, HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        Map<String, Object> canvas = canvasData(row);
        canvas.put("updated_at", System.currentTimeMillis());
        saveCanvasData(row, canvas);
        return Map.of("canvas", canvasRecord(row, false), "updated_at", millis(row.getUpdatedAt()));
    }

    @PutMapping("/canvases/{canvasId}")
    public Map<String, Object> saveCanvas(@PathVariable("canvasId") String canvasId,
                                          @RequestBody Map<String, Object> payload,
                                          HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        Map<String, Object> canvas = canvasData(row);
        canvas.putAll(cleanCanvasPayload(payload));
        canvas.put("id", String.valueOf(row.getId()));
        canvas.put("title", firstNonBlank(textValue(canvas.get("title")), row.getProjectName(), "未命名画布"));
        canvas.put("kind", normalizeCanvasKind(textValue(canvas.get("kind"))));
        canvas.put("updated_at", System.currentTimeMillis());
        saveCanvasData(row, canvas);
        return Map.of("canvas", canvasRecord(row, true));
    }

    @DeleteMapping("/canvases/{canvasId}")
    public Map<String, Object> deleteCanvas(@PathVariable("canvasId") String canvasId, HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        Map<String, Object> canvas = canvasData(row);
        canvas.put("deleted", true);
        canvas.put("deleted_at", System.currentTimeMillis());
        saveCanvasData(row, canvas);
        return Map.of("ok", true);
    }

    @PostMapping("/canvases/{canvasId}/restore")
    public Map<String, Object> restoreCanvas(@PathVariable("canvasId") String canvasId, HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        Map<String, Object> canvas = canvasData(row);
        canvas.put("deleted", false);
        canvas.remove("deleted_at");
        saveCanvasData(row, canvas);
        return Map.of("canvas", canvasRecord(row, false));
    }

    @DeleteMapping("/canvases/{canvasId}/purge")
    public Map<String, Object> purgeCanvas(@PathVariable("canvasId") String canvasId, HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        canvasProjectRepository.delete(row);
        return Map.of("ok", true);
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "image_model", "project-image",
                "image_models", List.of("project-image"),
                "chat_models", textModels(),
                "ms_chat_models", textModels(),
                "video_models", List.of("project-video"),
                "comfy_instances", List.of(),
                "api_providers", List.of(projectProvider())
        );
    }

    @GetMapping("/models")
    public Map<String, Object> models() {
        return Map.of("models", textModels(), "image_models", List.of("project-image"), "video_models", List.of("project-video"));
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of("providers", List.of(projectProvider()));
    }

    @GetMapping("/config/token")
    public Map<String, Object> tokenConfig() {
        return Map.of("has_token", true, "token", "", "key_preview", "后端托管");
    }

    @GetMapping("/workflows")
    public Map<String, Object> workflows() {
        return Map.of("workflows", List.of());
    }

    @GetMapping("/image-params")
    public Map<String, Object> imageParams() {
        return Map.of(
                "engine", "api",
                "submit", "/api/canvas-image-tasks",
                "fields", List.of(
                        Map.of(
                                "key", "size",
                                "type", "size",
                                "label", "尺寸",
                                "ratios", List.of("1:1", "3:4", "4:3", "16:9", "9:16"),
                                "resolutions", List.of("1k", "2k", "4k"),
                                "default", Map.of("ratio", "1:1", "resolution", "2k")
                        ),
                        Map.of("key", "n", "type", "int", "label", "数量", "options", List.of(1, 2, 3, 4), "default", 1),
                        Map.of("key", "reference_images", "type", "refs", "label", "参考图", "max", 8)
                )
        );
    }

    @PostMapping("/canvas-image-tasks")
    public Map<String, Object> createCanvasImageTask(@RequestBody Map<String, Object> payload,
                                                     HttpServletRequest request) {
        String prompt = firstNonBlank(textValue(payload.get("prompt")), "Edit the reference images.");
        List<String> refs = mediaUrls(payload.get("reference_images"));
        String inputUrl = refs.isEmpty() ? "" : normalizeInputUrl(refs.get(0));
        String colorUrl = refs.size() > 1
                ? refs.subList(1, refs.size()).stream().map(this::normalizeInputUrl).collect(Collectors.joining(","))
                : "";
        String size = textValue(payload.get("size"));
        String model = normalizeImageModel(textValue(payload.get("model")));
        String taskId = kieClientService.createTask(
                "AI_CANVAS",
                prompt,
                resolutionFromSize(size),
                aspectRatioFromSize(size),
                model,
                inputUrl,
                colorUrl,
                appProperties.getKie().getCallbackUrl()
        );
        canvasTaskService.recordCreated(taskId, "image", currentOperator(request), currentShopName(request));
        return Map.of("task_id", taskId, "status", "queued");
    }

    @GetMapping("/canvas-image-tasks/{taskId}")
    public Map<String, Object> getCanvasImageTask(@PathVariable("taskId") String taskId) {
        return taskResponse(taskId, "image");
    }

    @PostMapping("/image-task-query")
    public Map<String, Object> queryImageTask(@RequestBody Map<String, Object> payload) {
        String taskId = firstNonBlank(textValue(payload.get("task_id")), textValue(payload.get("taskId")));
        Map<String, Object> task = taskResponse(taskId, "image");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) task.getOrDefault("result", Map.of());
        Map<String, Object> response = new LinkedHashMap<>(task);
        response.putAll(result);
        return response;
    }

    @PostMapping("/online-image")
    public Map<String, Object> onlineImage(@RequestBody Map<String, Object> payload,
                                           HttpServletRequest request) throws InterruptedException {
        String taskId = textValue(createCanvasImageTask(payload, request).get("task_id"));
        KieTaskResult result = waitForTask(taskId, "image", 180_000L, 3_000L);
        if (!result.isSuccess()) {
            throw new RuntimeException(firstNonBlank(result.getErrorMessage(), "图片生成失败"));
        }
        return Map.of("images", List.of(result.getResultUrl()), "task_id", taskId);
    }

    @PostMapping("/canvas-video")
    public Map<String, Object> canvasVideo(@RequestBody Map<String, Object> payload,
                                           HttpServletRequest request) throws InterruptedException {
        String prompt = firstNonBlank(textValue(payload.get("prompt")), "Generate a video.");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        copyIfPresent(payload, input, "duration");
        copyIfPresent(payload, input, "aspect_ratio");
        copyIfPresent(payload, input, "resolution");
        copyIfPresent(payload, input, "enhance_prompt");
        copyIfPresent(payload, input, "generate_audio");
        List<String> images = mediaUrls(payload.get("images")).stream().map(this::normalizeInputUrl).toList();
        if (!images.isEmpty()) input.put("image_input", images);
        String model = normalizeVideoModel(textValue(payload.get("model")));
        KieTaskResult created = kieClientService.createVideoTask(model, input);
        String taskId = created.getTaskId();
        canvasTaskService.recordCreated(taskId, "video", currentOperator(request), currentShopName(request));
        KieTaskResult result = waitForTask(taskId, "video", 600_000L, 8_000L);
        if (!result.isSuccess()) {
            throw new RuntimeException(firstNonBlank(result.getErrorMessage(), "视频生成失败或超时，taskId=" + taskId));
        }
        return Map.of("videos", List.of(result.getResultUrl()), "task_id", taskId);
    }

    @PostMapping("/canvas-llm")
    public Map<String, Object> canvasLlm(@RequestBody Map<String, Object> payload) {
        String systemPrompt = firstNonBlank(textValue(payload.get("system_prompt")), "You are a helpful assistant.");
        String message = firstNonBlank(textValue(payload.get("message")), textValue(payload.get("prompt")));
        String model = KieGptModels.normalizeTextModel(textValue(payload.get("model")));
        String text = textModelService.generateRawPrompt(systemPrompt, message, model);
        return Map.of("text", text, "model", model);
    }

    @PostMapping(value = {"/ai/upload", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                      @RequestParam(value = "file", required = false) MultipartFile file) {
        List<MultipartFile> incoming = files == null ? new ArrayList<>() : new ArrayList<>(files);
        if (file != null && !file.isEmpty()) incoming.add(file);
        List<Map<String, Object>> uploaded = incoming.stream()
                .filter(item -> item != null && !item.isEmpty())
                .map(this::uploadFile)
                .toList();
        return Map.of("files", uploaded);
    }

    @PostMapping("/ai/upload-base64")
    public Map<String, Object> uploadBase64(@RequestBody Map<String, Object> payload) {
        String dataUrl = firstNonBlank(textValue(payload.get("data")), textValue(payload.get("data_url")), textValue(payload.get("url")));
        String name = firstNonBlank(textValue(payload.get("name")), "canvas-upload.png");
        String url = normalizeInputUrl(dataUrl);
        return Map.of("files", List.of(Map.of("url", url, "name", name, "kind", mediaKind(name, url))));
    }

    @PostMapping("/ai/import-local-image")
    public Map<String, Object> importLocalImage() {
        return Map.of("files", List.of());
    }

    @GetMapping("/canvas-assets")
    public Map<String, Object> canvasAssets() {
        return Map.of("assets", List.of());
    }

    @PostMapping("/canvas-assets/check")
    public Map<String, Object> checkCanvasAssets(@RequestBody Map<String, Object> payload) {
        Map<String, Object> exists = new LinkedHashMap<>();
        for (String url : mediaUrls(payload.get("urls"))) {
            exists.put(url, true);
        }
        return Map.of("exists", exists);
    }

    @PostMapping("/canvas-assets/download")
    public Map<String, Object> canvasAssetsDownload() {
        throw new RuntimeException("当前接入模式暂不支持本地资源打包下载");
    }

    @GetMapping("/asset-library")
    public Map<String, Object> assetLibrary() {
        return Map.of("library", emptyAssetLibrary());
    }

    @PostMapping({"/asset-library/items", "/asset-library/items/batch", "/asset-library/categories", "/asset-library/libraries"})
    public Map<String, Object> mutateAssetLibrary() {
        return assetLibrary();
    }

    @DeleteMapping({"/asset-library/items/{id}", "/asset-library/categories/{id}", "/asset-library/libraries/{id}"})
    public Map<String, Object> deleteAssetLibraryItem() {
        return assetLibrary();
    }

    @GetMapping("/local-assets")
    public Map<String, Object> localAssets() {
        return Map.of("items", List.of(), "tree", Map.of("id", "__root__", "name", "全部上传", "items", List.of(), "children", List.of()));
    }

    @GetMapping("/prompt-libraries")
    public Map<String, Object> promptLibraries() {
        return Map.of("library", Map.of(
                "active_library_id", "system",
                "libraries", List.of(Map.of("id", "system", "name", "系统提示词库", "readonly", true, "items", List.of()))
        ));
    }

    @PostMapping({"/prompt-libraries", "/prompt-libraries/items", "/prompt-libraries/items/delete", "/prompt-libraries/categories"})
    public Map<String, Object> mutatePromptLibraries() {
        return promptLibraries();
    }

    @DeleteMapping({"/prompt-libraries/{id}", "/prompt-libraries/items/{id}", "/prompt-libraries/categories/{id}"})
    public Map<String, Object> deletePromptLibraryItem() {
        return promptLibraries();
    }

    @GetMapping("/smart-canvas/prompt-templates")
    public Map<String, Object> smartCanvasPromptTemplates() {
        return Map.of("templates", List.of(), "source", "ai-project");
    }

    @PostMapping("/canvas-comfy-tasks")
    public Map<String, Object> canvasComfyTask() {
        String taskId = "canvas_comfy_" + UUID.randomUUID();
        return Map.of("task_id", taskId, "status", "failed", "error", "当前项目未启用 ComfyUI");
    }

    @GetMapping("/canvas-comfy-tasks/{taskId}")
    public Map<String, Object> getCanvasComfyTask(@PathVariable("taskId") String taskId) {
        return Map.of("id", taskId, "status", "failed", "error", "当前项目未启用 ComfyUI");
    }

    private List<Map<String, Object>> workspaceProjects(HttpServletRequest request, List<CanvasProject> rows) {
        CanvasProject workspace = workspaceRow(request, rows);
        List<Map<String, Object>> projects = readWorkspaceProjects(workspace);
        List<CanvasProject> activeRows = rows.stream()
                .filter(this::isInfiniteCanvasRow)
                .filter(row -> !boolValue(readMeta(row).get("deleted")))
                .toList();
        return projects.stream()
                .map(project -> decorateProject(project, activeRows, projects))
                .sorted(Comparator.comparingInt(item -> intValue(item.get("order"), 0)))
                .toList();
    }

    private Map<String, Object> decorateProject(Map<String, Object> project,
                                                List<CanvasProject> activeRows,
                                                List<Map<String, Object>> projects) {
        Map<String, Object> result = new LinkedHashMap<>(project);
        String id = textValue(result.get("id"));
        long count = activeRows.stream()
                .map(this::canvasData)
                .filter(canvas -> id.equals(firstNonBlank(textValue(canvas.get("project")), DEFAULT_PROJECT_ID)))
                .count();
        result.put("canvas_count", count);
        result.putIfAbsent("name", DEFAULT_PROJECT_ID.equals(id) ? "默认项目" : "未命名项目");
        result.putIfAbsent("order", DEFAULT_PROJECT_ID.equals(id) ? 0 : projects.indexOf(project) + 1);
        result.putIfAbsent("updated_at", System.currentTimeMillis());
        return result;
    }

    private CanvasProject workspaceRow(HttpServletRequest request) {
        return workspaceRow(request, ownedRows(request));
    }

    private CanvasProject workspaceRow(HttpServletRequest request, List<CanvasProject> rows) {
        Optional<CanvasProject> existing = rows.stream().filter(this::isWorkspaceRow).findFirst();
        if (existing.isPresent()) return existing.get();
        CanvasProject row = new CanvasProject();
        row.setOperator(currentOperator(request));
        row.setShopName(currentShopName(request));
        row.setProjectName(WORKSPACE_PROJECT_NAME);
        row.setSnapshotJson("{}");
        row.setMetaJson(writeJson(Map.of("kind", WORKSPACE_KIND, "projects", defaultProjects())));
        return canvasProjectRepository.save(row);
    }

    private List<Map<String, Object>> readWorkspaceProjects(CanvasProject row) {
        Map<String, Object> meta = readMeta(row);
        Object rawProjects = meta.get("projects");
        List<Map<String, Object>> projects = new ArrayList<>();
        if (rawProjects instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    projects.add(toStringObjectMap(map));
                }
            }
        }
        if (projects.stream().noneMatch(item -> DEFAULT_PROJECT_ID.equals(textValue(item.get("id"))))) {
            projects.add(0, new LinkedHashMap<>(Map.of("id", DEFAULT_PROJECT_ID, "name", "默认项目", "order", 0, "updated_at", System.currentTimeMillis())));
        }
        return projects;
    }

    private void saveWorkspaceProjects(CanvasProject row, List<Map<String, Object>> projects) {
        Map<String, Object> meta = readMeta(row);
        meta.put("kind", WORKSPACE_KIND);
        meta.put("projects", projects);
        row.setProjectName(WORKSPACE_PROJECT_NAME);
        row.setMetaJson(writeJson(meta));
        row.setSnapshotJson("{}");
        canvasProjectRepository.save(row);
    }

    private List<Map<String, Object>> defaultProjects() {
        return List.of(new LinkedHashMap<>(Map.of("id", DEFAULT_PROJECT_ID, "name", "默认项目", "order", 0, "updated_at", System.currentTimeMillis())));
    }

    private List<CanvasProject> ownedRows(HttpServletRequest request) {
        return canvasProjectRepository.findByShopNameAndOperatorOrderByUpdatedAtDesc(currentShopName(request), currentOperator(request));
    }

    private List<CanvasProject> activeCanvasRows(HttpServletRequest request) {
        return ownedRows(request).stream()
                .filter(this::isInfiniteCanvasRow)
                .filter(row -> !boolValue(readMeta(row).get("deleted")))
                .toList();
    }

    private CanvasProject ownedCanvasRow(String id, HttpServletRequest request) {
        Long rowId = parseLong(id);
        CanvasProject row = canvasProjectRepository.findById(rowId)
                .orElseThrow(() -> new RuntimeException("画布不存在"));
        if (!Objects.equals(row.getOperator(), currentOperator(request)) || !Objects.equals(row.getShopName(), currentShopName(request))) {
            throw new RuntimeException("无权访问该画布");
        }
        if (!isInfiniteCanvasRow(row)) {
            throw new RuntimeException("画布不存在");
        }
        return row;
    }

    private boolean isWorkspaceRow(CanvasProject row) {
        return WORKSPACE_PROJECT_NAME.equals(row.getProjectName()) || WORKSPACE_KIND.equals(textValue(readMeta(row).get("kind")));
    }

    private boolean isInfiniteCanvasRow(CanvasProject row) {
        return CANVAS_KIND.equals(textValue(readMeta(row).get("kind")));
    }

    private Map<String, Object> defaultCanvas(CanvasProject row) {
        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("id", String.valueOf(row.getId()));
        canvas.put("title", firstNonBlank(row.getProjectName(), "未命名画布"));
        canvas.put("icon", "layers");
        canvas.put("kind", "classic");
        canvas.put("project", DEFAULT_PROJECT_ID);
        canvas.put("nodes", List.of());
        canvas.put("connections", List.of());
        canvas.put("viewport", Map.of("x", 0, "y", 0, "scale", 1));
        canvas.put("logs", List.of());
        canvas.put("created_at", millis(row.getCreatedAt()));
        canvas.put("updated_at", millis(row.getUpdatedAt()));
        canvas.put("deleted", false);
        return canvas;
    }

    private Map<String, Object> canvasData(CanvasProject row) {
        Map<String, Object> canvas = readMap(row.getSnapshotJson());
        if (canvas.isEmpty()) canvas = defaultCanvas(row);
        Map<String, Object> meta = readMeta(row);
        canvas.put("id", String.valueOf(row.getId()));
        canvas.put("title", firstNonBlank(textValue(canvas.get("title")), row.getProjectName(), "未命名画布"));
        canvas.put("icon", firstNonBlank(textValue(canvas.get("icon")), "layers"));
        canvas.put("kind", normalizeCanvasKind(textValue(canvas.get("kind"))));
        canvas.put("project", firstNonBlank(textValue(canvas.get("project")), textValue(meta.get("project")), DEFAULT_PROJECT_ID));
        canvas.put("created_at", firstLong(canvas.get("created_at"), millis(row.getCreatedAt())));
        canvas.put("updated_at", firstLong(canvas.get("updated_at"), millis(row.getUpdatedAt())));
        canvas.put("deleted", boolValue(firstValue(canvas.get("deleted"), meta.get("deleted"))));
        if (meta.containsKey("deleted_at")) canvas.put("deleted_at", meta.get("deleted_at"));
        canvas.putIfAbsent("nodes", List.of());
        canvas.putIfAbsent("connections", List.of());
        canvas.putIfAbsent("viewport", Map.of("x", 0, "y", 0, "scale", 1));
        canvas.putIfAbsent("logs", List.of());
        return canvas;
    }

    private Map<String, Object> canvasRecord(CanvasProject row, boolean full) {
        Map<String, Object> canvas = canvasData(row);
        Map<String, Object> result = full ? new LinkedHashMap<>(canvas) : new LinkedHashMap<>();
        for (String key : List.of("id", "title", "icon", "kind", "project", "board_x", "board_y", "owner", "color", "pinned", "created_at", "updated_at", "deleted_at")) {
            if (canvas.containsKey(key)) result.put(key, canvas.get(key));
        }
        result.put("node_count", canvas.get("nodes") instanceof List<?> nodes ? nodes.size() : 0);
        return result;
    }

    private void saveCanvasData(CanvasProject row, Map<String, Object> canvas) {
        String title = firstNonBlank(textValue(canvas.get("title")), "未命名画布");
        canvas.put("id", String.valueOf(row.getId()));
        canvas.put("title", title);
        canvas.put("kind", normalizeCanvasKind(textValue(canvas.get("kind"))));
        canvas.put("project", firstNonBlank(textValue(canvas.get("project")), DEFAULT_PROJECT_ID));
        row.setProjectName(title);
        row.setSnapshotJson(writeJson(canvas));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", CANVAS_KIND);
        for (String key : List.of("project", "board_x", "board_y", "owner", "color", "pinned", "deleted", "deleted_at")) {
            if (canvas.containsKey(key)) meta.put(key, canvas.get(key));
        }
        row.setMetaJson(writeJson(meta));
        canvasProjectRepository.save(row);
    }

    private Map<String, Object> cleanCanvasPayload(Map<String, Object> payload) {
        Map<String, Object> clean = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        clean.remove("id");
        clean.remove("client_id");
        clean.remove("base_updated_at");
        return clean;
    }

    private Comparator<Map<String, Object>> canvasComparator() {
        return Comparator
                .<Map<String, Object>>comparingInt(item -> boolValue(item.get("pinned")) ? 0 : 1)
                .thenComparing((a, b) -> Long.compare(firstLong(b.get("updated_at"), 0L), firstLong(a.get("updated_at"), 0L)));
    }

    private Map<String, Object> projectProvider() {
        return Map.of(
                "id", "ai-project-kie",
                "name", "项目 KIE 代理",
                "base_url", "/api/canvas/kie/v1",
                "enabled", true,
                "has_key", false,
                "key_preview", "后端托管",
                "image_models", List.of("project-image"),
                "chat_models", textModels(),
                "video_models", List.of("project-video")
        );
    }

    private List<String> textModels() {
        return List.of(KieGptModels.GPT_5_6_SOL, KieGptModels.GPT_5_6_TERRA, KieGptModels.GPT_5_6_LUNA);
    }

    private Map<String, Object> emptyAssetLibrary() {
        Map<String, Object> imageCategory = new LinkedHashMap<>();
        imageCategory.put("id", "default-images");
        imageCategory.put("name", "默认分组");
        imageCategory.put("type", "image");
        imageCategory.put("items", List.of());

        Map<String, Object> workflowCategory = new LinkedHashMap<>();
        workflowCategory.put("id", "default-workflows");
        workflowCategory.put("name", "工作流");
        workflowCategory.put("type", "workflow");
        workflowCategory.put("items", List.of());

        Map<String, Object> library = new LinkedHashMap<>();
        library.put("id", "default");
        library.put("name", "默认资产库");
        library.put("categories", List.of(imageCategory, workflowCategory));

        return Map.of("active_library_id", "default", "libraries", List.of(library), "categories", List.of(imageCategory, workflowCategory));
    }

    private Map<String, Object> taskResponse(String taskId, String mediaType) {
        KieTaskResult result = readTaskResult(taskId);
        String status = normalizeTaskStatus(result);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", taskId);
        body.put("task_id", taskId);
        body.put("status", status);
        body.put("error", firstNonBlank(result.getErrorMessage(), ""));
        if ("succeeded".equals(status) && result.getResultUrl() != null && !result.getResultUrl().isBlank()) {
            String url = result.getResultUrl();
            body.put("result", Map.of(mediaType.equals("video") ? "videos" : "images", List.of(url), "url", url));
        } else {
            body.put("result", Map.of(mediaType.equals("video") ? "videos" : "images", List.of()));
        }
        return body;
    }

    private KieTaskResult readTaskResult(String taskId) {
        Optional<KieTaskResult> stored = canvasTaskService.findResult(taskId);
        if (stored.isPresent() && stored.get().isFinished()) {
            return stored.get();
        }
        KieTaskResult result = kieClientService.getFullResult(taskId);
        canvasTaskService.recordPolledResult(result);
        return result;
    }

    private KieTaskResult waitForTask(String taskId, String mediaType, long maxWaitMs, long intervalMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        KieTaskResult latest = KieTaskResult.builder().taskId(taskId).status("PROCESSING").finished(false).build();
        while (System.currentTimeMillis() < deadline) {
            latest = readTaskResult(taskId);
            if (latest.isFinished()) return latest;
            Thread.sleep(intervalMs);
        }
        latest.setStatus("FAILED");
        latest.setFinished(true);
        latest.setSuccess(false);
        latest.setErrorMessage(mediaType + " 任务等待超时，请稍后到画布输出节点重新查询");
        return latest;
    }

    private String normalizeTaskStatus(KieTaskResult result) {
        if (result == null) return "running";
        if (result.isSuccess() || "SUCCESS".equalsIgnoreCase(result.getStatus())) return "succeeded";
        if (result.isFinished() || "FAILED".equalsIgnoreCase(result.getStatus())) return "failed";
        return "running";
    }

    private Map<String, Object> uploadFile(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String name = firstNonBlank(file.getOriginalFilename(), "canvas-upload.bin");
            String contentType = firstNonBlank(file.getContentType(), "application/octet-stream");
            String ext = fileExtension(name, contentType);
            String objectName = "AI_CANVAS/upload/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            ossService.getOssClient().putObject(
                    appProperties.getOss().getInputBucket(),
                    objectName,
                    new ByteArrayInputStream(bytes),
                    metadata
            );
            String url = appProperties.getOss().getInputPublicHost() + "/" + objectName;
            return Map.of("url", url, "name", name, "kind", mediaKind(name, url), "comfy_name", name);
        } catch (Exception e) {
            throw new RuntimeException("上传文件失败: " + e.getMessage(), e);
        }
    }

    private String normalizeInputUrl(String value) {
        String raw = textValue(value).trim();
        if (raw.isBlank() || !raw.startsWith("data:")) return raw;
        try {
            int comma = raw.indexOf(',');
            if (comma < 0) return raw;
            String header = raw.substring(0, comma);
            String base64 = raw.substring(comma + 1);
            if (base64.contains("%")) {
                base64 = URLDecoder.decode(base64, StandardCharsets.UTF_8);
            }
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            String contentType = header.contains(";") ? header.substring(5, header.indexOf(';')) : "image/png";
            String ext = fileExtension("upload", contentType);
            String objectName = "AI_CANVAS/input/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ext;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(contentType);
            ossService.getOssClient().putObject(
                    appProperties.getOss().getInputBucket(),
                    objectName,
                    new ByteArrayInputStream(bytes),
                    metadata
            );
            return appProperties.getOss().getInputPublicHost() + "/" + objectName;
        } catch (Exception e) {
            log.warn("[Infinite Canvas] data url 上传 OSS 失败，回退原始内容: {}", e.getMessage());
            return raw;
        }
    }

    private List<String> mediaUrls(Object value) {
        if (value == null) return List.of();
        List<String> urls = new ArrayList<>();
        collectMediaUrls(value, urls);
        return urls.stream().filter(item -> !item.isBlank()).distinct().toList();
    }

    private void collectMediaUrls(Object value, List<String> urls) {
        if (value == null) return;
        if (value instanceof String str) {
            urls.add(str);
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectMediaUrls(item, urls));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("url", "image_url", "imageUrl", "src", "output", "video_url", "videoUrl")) {
                if (map.containsKey(key)) collectMediaUrls(map.get(key), urls);
            }
        }
    }

    private String resolutionFromSize(String size) {
        String raw = size == null ? "" : size.toLowerCase();
        if (raw.contains("4096") || raw.contains("3840") || raw.contains("4k")) return "4K";
        if (raw.contains("2048") || raw.contains("2k")) return "2K";
        if (raw.contains("1024") || raw.contains("1k")) return "1K";
        return "2K";
    }

    private String aspectRatioFromSize(String size) {
        String raw = size == null ? "" : size.toLowerCase();
        String[] parts = raw.split("x");
        if (parts.length != 2) return "auto";
        try {
            int w = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
            int h = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
            int gcd = gcd(w, h);
            return (w / gcd) + ":" + (h / gcd);
        } catch (Exception e) {
            return "auto";
        }
    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return Math.max(1, Math.abs(a));
    }

    private String normalizeImageModel(String model) {
        if (model == null || model.isBlank() || model.startsWith("project-")) return PROJECT_IMAGE_MODEL;
        return model;
    }

    private String normalizeVideoModel(String model) {
        if (model == null || model.isBlank() || model.startsWith("project-")) return PROJECT_VIDEO_MODEL;
        return model;
    }

    private String normalizeCanvasKind(String value) {
        return "smart".equalsIgnoreCase(value) ? "smart" : "classic";
    }

    private void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.containsKey(key)) to.put(key, from.get(key));
    }

    private Map<String, Object> readMeta(CanvasProject row) {
        return readMap(row.getMetaJson());
    }

    private Map<String, Object> readMap(String raw) {
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new RuntimeException("画布数据序列化失败", e);
        }
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Object firstValue(Object first, Object fallback) {
        return first != null ? first : fallback;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new RuntimeException("无效画布 ID");
        }
    }

    private long millis(LocalDateTime value) {
        if (value == null) return System.currentTimeMillis();
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long firstLong(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            String text = textValue(value);
            return text.isBlank() ? fallback : Long.parseLong(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            String text = textValue(value);
            return text.isBlank() ? fallback : Integer.parseInt(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean boolValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        return "true".equalsIgnoreCase(textValue(value)) || "1".equals(textValue(value));
    }

    private String textValue(Object value) {
        if (value == null) return "";
        if (value instanceof String str) return str;
        return String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
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

    private String fileExtension(String filename, String contentType) {
        String name = filename == null ? "" : filename;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) return name.substring(dot);
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.contains("jpeg") || type.contains("jpg")) return ".jpg";
        if (type.contains("png")) return ".png";
        if (type.contains("webp")) return ".webp";
        if (type.contains("gif")) return ".gif";
        if (type.contains("mp4")) return ".mp4";
        if (type.contains("webm")) return ".webm";
        if (type.contains("mpeg") || type.contains("mp3")) return ".mp3";
        return ".bin";
    }

    private String mediaKind(String name, String url) {
        String text = (firstNonBlank(name, url)).toLowerCase();
        if (text.endsWith(".mp4") || text.endsWith(".webm") || text.endsWith(".mov")) return "video";
        if (text.endsWith(".mp3") || text.endsWith(".wav") || text.endsWith(".m4a")) return "audio";
        return "image";
    }
}
