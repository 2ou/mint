package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.entity.CanvasProject;
import com.ai.repository.CanvasProjectRepository;
import com.ai.service.CanvasMediaCleanupService;
import com.ai.service.CanvasTaskService;
import com.ai.service.KieClientService;
import com.ai.service.ModelPricingService;
import com.ai.service.OssService;
import com.ai.service.TextModelService;
import com.ai.service.impl.KieGptModels;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class InfiniteCanvasController {

    private static final String WORKSPACE_KIND = "infinite-canvas-workspace";
    private static final String CANVAS_KIND = "infinite-canvas";
    private static final String LIBRARY_KIND = "infinite-canvas-library";
    private static final String WORKSPACE_PROJECT_NAME = "__infinite_canvas_workspace__";
    private static final String LIBRARY_PROJECT_NAME = "__infinite_canvas_library__";
    private static final String DEFAULT_PROJECT_ID = "default";
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
    private static final long KIE_IMAGE_UPLOAD_MAX_BYTES = 10L * 1024 * 1024;
    private static final long KIE_MEDIA_UPLOAD_MAX_BYTES = 100L * 1024 * 1024;
    private static final long KIE_VIDEO_REFERENCE_IMAGE_MAX_BYTES = 30L * 1024 * 1024;
    private static final long KIE_VIDEO_REFERENCE_AUDIO_MAX_BYTES = 15L * 1024 * 1024;
    private static final long WORKFLOW_ARCHIVE_MAX_BYTES = 220L * 1024 * 1024;
    private static final long MEDIA_PROXY_MAX_BYTES = 110L * 1024 * 1024;
    private static final int MEDIA_PROXY_MAX_REDIRECTS = 3;
    private static final Set<String> KIE_MEDIA_HOST_SUFFIXES = Set.of(
            ".kie.ai", ".aiquickdraw.com", ".redpandaai.co"
    );
    private static final Set<String> KIE_IMAGE_ASPECT_RATIOS = Set.of(
            "1:1", "16:9", "9:16", "4:3", "3:4", "4:5", "5:4", "3:2", "2:3", "21:9", "9:21"
    );
    private static final Set<String> LIBLIB_MEDIA_HOSTS = Set.of(
            "libtv-res.liblib.art", "liblibai-online.liblib.cloud"
    );

    private final CanvasProjectRepository canvasProjectRepository;
    private final CanvasMediaCleanupService canvasMediaCleanupService;
    private final CanvasTaskService canvasTaskService;
    private final KieClientService kieClientService;
    private final ModelPricingService modelPricingService;
    private final TextModelService textModelService;
    private final OssService ossService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient mediaProxyClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    @GetMapping("/download-output")
    public void downloadOutput(@RequestParam("url") String url,
                               @RequestParam(value = "name", required = false) String name,
                               @RequestParam(value = "inline", defaultValue = "false") boolean inline,
                               HttpServletResponse response) throws IOException {
        if (url != null && url.startsWith("/ai-result/")) {
            serveLocalFile(url, name, inline, response);
            return;
        }
        proxyTrustedMedia(url, name, inline, response);
    }

    /**
     * 本地落盘结果图（/ai-result/**）直接以附件形式回传原文件字节，供画布「下载图片」使用。
     * 复用与 serveLocalResized 相同的路径穿越防护与 localSaveRoot 解析逻辑。
     */
    private void serveLocalFile(String relativeUrl, String requestedName, boolean inline,
                                HttpServletResponse response) throws IOException {
        String root = appProperties.getLocalSaveRoot();
        if (root == null || root.isBlank()) {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            root = os.contains("win") ? "D:/AiResult" : "/tmp/ai-result";
        }
        String rel = relativeUrl.substring("/ai-result/".length());
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        Path filePath = rootPath.resolve(rel).normalize();
        if (!filePath.startsWith(rootPath) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "本地文件不存在");
            return;
        }
        String filename = safeMediaFilename(requestedName, filePath.toUri());
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=300");
        response.setHeader("Content-Disposition", contentDisposition(filename, inline));
        response.setContentLengthLong(Files.size(filePath));
        try (InputStream in = Files.newInputStream(filePath);
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    @GetMapping("/media-preview")
    public void mediaPreview(@RequestParam("url") String url,
                             @RequestParam(value = "w", required = false) Integer width,
                             HttpServletResponse response) throws IOException {
        if (url != null && url.startsWith("/ai-result/")) {
            serveLocalResized(url, width, response);
            return;
        }
        proxyTrustedMedia(url, "canvas-preview", true, response);
    }

    /**
     * 本地落盘结果图（/ai-result/**）按尺寸缩放并转 JPEG 输出，避免局域网直接拉 6~19MB 原图。
     * 仅读取 localSaveRoot 目录下文件，并做路径穿越防护。
     */
    private void serveLocalResized(String relativeUrl, Integer requestedWidth, HttpServletResponse response) throws IOException {
        String root = appProperties.getLocalSaveRoot();
        if (root == null || root.isBlank()) {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            root = os.contains("win") ? "D:/AiResult" : "/tmp/ai-result";
        }
        String rel = relativeUrl.substring("/ai-result/".length());
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        Path filePath = rootPath.resolve(rel).normalize();
        if (!filePath.startsWith(rootPath) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "本地预览文件不存在");
            return;
        }
        BufferedImage image;
        try (InputStream in = Files.newInputStream(filePath)) {
            image = ImageIO.read(in);
        }
        if (image == null) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "无法解析的图片文件");
            return;
        }
        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();
        int targetWidth = (requestedWidth != null && requestedWidth > 0)
                ? Math.max(32, Math.min(2048, requestedWidth)) : srcWidth;
        BufferedImage out = image;
        if (targetWidth < srcWidth) {
            int targetHeight = (int) Math.round(srcHeight * (targetWidth / (double) srcWidth));
            out = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(image, 0, 0, targetWidth, targetHeight, null);
            g.dispose();
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "public, max-age=86400");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.82f);
            }
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(response.getOutputStream())) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }

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

    @PostMapping("/canvases/{canvasId}/logs/delete")
    public ResponseEntity<Map<String, Object>> deleteCanvasLog(@PathVariable("canvasId") String canvasId,
                                                                @RequestBody Map<String, Object> payload,
                                                                HttpServletRequest request) {
        CanvasProject row = ownedCanvasRow(canvasId, request);
        Map<String, Object> canvas = canvasData(row);
        long baseUpdatedAt = firstLong(payload.get("base_updated_at"), 0L);
        long currentUpdatedAt = firstLong(canvas.get("updated_at"), 0L);
        if (baseUpdatedAt > 0L && currentUpdatedAt > baseUpdatedAt) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("detail", "画布已在其他位置更新，请刷新后重试");
            body.put("canvas", canvasRecord(row, true));
            body.put("updated_at", currentUpdatedAt);
            return ResponseEntity.status(409).body(body);
        }

        try {
            CanvasMediaCleanupService.CleanupPlan plan = canvasMediaCleanupService.prepare(
                    canvas,
                    textValue(payload.get("log_id")),
                    boolValue(payload.get("delete_unreferenced_media")),
                    boolValue(payload.get("reset_referencing_nodes"))
            );
            saveCanvasData(row, plan.canvas());
            CanvasMediaCleanupService.CleanupResult cleanup = canvasMediaCleanupService.finish(
                    plan, row.getOperator(), row.getShopName());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("canvas", canvasRecord(row, true));
            body.put("removed_files", cleanup.removedFiles());
            body.put("reset_node_ids", plan.resetNodeIds());
            body.put("skipped_referenced", cleanup.skippedReferenced());
            body.put("removed_task_ids", cleanup.removedTaskIds());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("detail", e.getMessage()));
        }
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
                "image_model", PROJECT_IMAGE_MODEL,
                "image_models", PROJECT_IMAGE_MODELS,
                "chat_models", textModels(),
                "ms_chat_models", textModels(),
                "video_models", PROJECT_VIDEO_MODELS,
                "comfy_instances", List.of(),
                "api_providers", List.of(projectProvider())
        );
    }

    @GetMapping("/models")
    public Map<String, Object> models() {
        return Map.of("models", textModels(), "image_models", PROJECT_IMAGE_MODELS, "video_models", PROJECT_VIDEO_MODELS);
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

    /** Server calculated quotation for the AI canvas confirmation dialog. */
    @PostMapping("/canvas-billing/quote")
    public Map<String, Object> quoteCanvasBilling(@RequestBody Map<String, Object> payload) {
        String mediaType = firstNonBlank(textValue(payload.get("media_type")), "image");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = payload.get("payload") instanceof Map<?, ?> map
                ? toStringObjectMap(map)
                : payload;
        int quantity = Math.max(1, intValue(payload.get("quantity"), 1));
        return modelPricingService.quote(mediaType, input, quantity).toMap();
    }

    /** Immutable per-canvas task ledger. Deleted nodes are intentionally not removed from it. */
    @GetMapping("/canvas-billing")
    public Map<String, Object> canvasBilling(@RequestParam("canvas_id") String canvasId,
                                             HttpServletRequest request) {
        CanvasProject owned = ownedCanvasRow(canvasId, request);
        return canvasTaskService.billingSummary(currentOperator(request), currentShopName(request), String.valueOf(owned.getId()));
    }

    @GetMapping("/admin/model-prices")
    public Map<String, Object> modelPriceCatalogue(HttpServletRequest request) {
        return Map.of(
                "catalogue", modelPricingService.currentCatalogue(),
                "rules", modelPricingService.currentRules(),
                "currency", "CNY",
                "credit_to_cny", "0.032",
                "can_edit", isPriceAdmin(request)
        );
    }

    @PutMapping("/admin/model-prices/rules")
    public Map<String, Object> replaceCurrentModelPriceRules(@RequestBody Map<String, Object> payload,
                                                        HttpServletRequest request) {
        requirePriceAdmin(request);
        List<Map<String, Object>> rules = new ArrayList<>();
        Object rawRules = payload.get("rules");
        if (rawRules instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) rules.add(toStringObjectMap(map));
            }
        }
        return Map.of("rules", modelPricingService.replaceCurrentRules(rules));
    }

    @PostMapping("/canvas-image-tasks")
    public Map<String, Object> createCanvasImageTask(@RequestBody Map<String, Object> payload,
                                                     HttpServletRequest request) {
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        canvasTaskService.requireSubmissionCapacity(operator, shopName);
        String prompt = firstNonBlank(textValue(payload.get("prompt")), "Edit the reference images.");
        List<String> refs = mediaUrls(payload.get("reference_images"));
        String inputUrl = refs.isEmpty() ? "" : normalizeInputUrl(refs.get(0));
        String colorUrl = refs.size() > 1
                ? refs.subList(1, refs.size()).stream().map(this::normalizeInputUrl).collect(Collectors.joining(","))
                : "";
        String resolution = explicitImageResolution(textValue(payload.get("resolution")));
        String aspectRatio = explicitImageAspectRatio(textValue(payload.get("aspect_ratio")));
        if (resolution.isBlank()) {
            throw new IllegalArgumentException("KIE 图片任务必须明确传 resolution：1K、2K 或 4K");
        }
        if (aspectRatio.isBlank()) {
            throw new IllegalArgumentException("KIE 图片任务必须明确传受支持的 aspect_ratio");
        }
        String model = normalizeImageModel(textValue(payload.get("model")));
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
        canvasTaskService.recordCreated(taskId, "image", operator, shopName, payload);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", taskId);
        response.put("status", "queued");
        response.put("completion_mode", useCallbackTaskCompletion() ? "callback" : "polling");
        response.putAll(canvasTaskService.billingFields(taskId));
        return response;
    }

    @GetMapping("/canvas-image-tasks/{taskId}")
    public Map<String, Object> getCanvasImageTask(@PathVariable("taskId") String taskId) {
        return taskResponse(taskId, "image");
    }

    @GetMapping("/canvas-tasks")
    public Map<String, Object> getCanvasTasks(@RequestParam(value = "canvas_id", required = false) String canvasId,
                                              HttpServletRequest request) {
        String scopedCanvasId = "";
        if (canvasId != null && !canvasId.isBlank()) {
            // Resolve through the owning canvas first: task history must never be
            // used to probe another user's canvas id.
            scopedCanvasId = String.valueOf(ownedCanvasRow(canvasId, request).getId());
        }
        List<Map<String, Object>> tasks = canvasTaskService.recentTasks(
                currentOperator(request), currentShopName(request), scopedCanvasId);
        Map<String, Long> summary = new LinkedHashMap<>();
        for (Map<String, Object> task : tasks) {
            String status = textValue(task.get("status"));
            summary.put(status, summary.getOrDefault(status, 0L) + 1L);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("canvas_id", scopedCanvasId);
        response.put("tasks", tasks);
        response.put("summary", summary);
        response.put("completion_mode", useCallbackTaskCompletion() ? "callback" : "polling");
        response.put("capacity", canvasTaskService.taskCapacity(currentOperator(request), currentShopName(request)));
        return response;
    }

    @PostMapping("/canvas-tasks/{taskId}/retry")
    public Map<String, Object> retryCanvasTask(@PathVariable("taskId") String taskId,
                                               HttpServletRequest request) {
        Map<String, Object> retry = canvasTaskService.retryPayload(taskId, currentOperator(request), currentShopName(request))
                .orElseThrow(() -> new IllegalArgumentException("该任务没有可恢复的请求参数，无法重试"));
        String mediaType = textValue(retry.get("media_type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = retry.get("payload") instanceof Map<?, ?> value
                ? toStringObjectMap(value)
                : Map.of();
        if (payload.isEmpty()) throw new IllegalArgumentException("该任务没有可恢复的请求参数，无法重试");

        Map<String, Object> created = "video".equalsIgnoreCase(mediaType)
                ? createCanvasVideoTask(payload, request)
                : createCanvasImageTask(payload, request);
        Map<String, Object> response = new LinkedHashMap<>(created);
        response.put("retry_of", taskId);
        response.put("media_type", "video".equalsIgnoreCase(mediaType) ? "video" : "image");
        return response;
    }

    @PostMapping("/canvas-image-tasks/{taskId}/cancel")
    public Map<String, Object> cancelCanvasImageTask(@PathVariable("taskId") String taskId) {
        boolean cancelled = canvasTaskService.cancelTracking(taskId);
        Map<String, Object> response = new LinkedHashMap<>(taskResponse(taskId, "image"));
        response.put("cancelled", cancelled);
        response.put("cancel_scope", "canvas_waiting");
        response.put("message", "已停止在画布中等待；KIE 服务端任务可能仍会继续完成。");
        return response;
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
        String taskId = textValue(createCanvasVideoTask(payload, request).get("task_id"));
        KieTaskResult result = waitForTask(taskId, "video", 600_000L, 8_000L);
        if (!result.isSuccess()) {
            throw new RuntimeException(firstNonBlank(result.getErrorMessage(), "视频生成失败或超时，taskId=" + taskId));
        }
        return Map.of("videos", List.of(result.getResultUrl()), "task_id", taskId);
    }

    @PostMapping("/canvas-video-tasks")
    public Map<String, Object> createCanvasVideoTask(@RequestBody Map<String, Object> payload,
                                                      HttpServletRequest request) {
        String operator = currentOperator(request);
        String shopName = currentShopName(request);
        canvasTaskService.requireSubmissionCapacity(operator, shopName);
        String prompt = firstNonBlank(textValue(payload.get("prompt")), "Generate a video.");
        String model = normalizeVideoModel(textValue(payload.get("model")));
        Map<String, Object> input = videoInput(payload, prompt, model);
        KieTaskResult created = kieClientService.createVideoTask(model, input);
        String taskId = created.getTaskId();
        canvasTaskService.recordCreated(taskId, "video", operator, shopName, payload);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task_id", taskId);
        response.put("status", "queued");
        response.put("completion_mode", useCallbackTaskCompletion() ? "callback" : "polling");
        response.putAll(canvasTaskService.billingFields(taskId));
        return response;
    }

    @GetMapping("/canvas-video-tasks/{taskId}")
    public Map<String, Object> getCanvasVideoTask(@PathVariable("taskId") String taskId) {
        return taskResponse(taskId, "video");
    }

    @PostMapping("/canvas-video-tasks/{taskId}/cancel")
    public Map<String, Object> cancelCanvasVideoTask(@PathVariable("taskId") String taskId) {
        boolean cancelled = canvasTaskService.cancelTracking(taskId);
        Map<String, Object> response = new LinkedHashMap<>(taskResponse(taskId, "video"));
        response.put("cancelled", cancelled);
        response.put("cancel_scope", "canvas_waiting");
        response.put("message", "已停止在画布中等待；KIE 服务端任务可能仍会继续完成。");
        return response;
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
    public Map<String, Object> getAssetLibrary(HttpServletRequest request) {
        return Map.of("library", assetLibraryState(request));
    }

    @PostMapping("/asset-library/libraries")
    public Map<String, Object> createAssetLibrary(@RequestBody Map<String, Object> payload,
                                                   HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        Map<String, Object> created = newAssetLibraryEntry(firstNonBlank(textValue(payload.get("name")), "我的素材库"));
        mapList(library, "libraries").add(created);
        library.put("active_library_id", created.get("id"));
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "library_item", created);
    }

    @PostMapping("/asset-library/categories")
    public Map<String, Object> createAssetCategory(@RequestBody Map<String, Object> payload,
                                                    HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        Map<String, Object> target = requireAssetLibrary(library, textValue(payload.get("library_id")));
        String type = "workflow".equalsIgnoreCase(textValue(payload.get("type"))) ? "workflow" : "image";
        Map<String, Object> category = newAssetCategory(
                firstNonBlank(textValue(payload.get("name")), type.equals("workflow") ? "工作流" : "图片素材"), type);
        mapList(target, "categories").add(category);
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "category", category);
    }

    @PostMapping("/asset-library/items")
    public Map<String, Object> createAssetItem(@RequestBody Map<String, Object> payload,
                                                HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> item = appendAssetItem(assetLibraryFromState(state), payload);
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "item", item);
    }

    @PostMapping("/asset-library/items/batch")
    public Map<String, Object> createAssetItems(@RequestBody Map<String, Object> payload,
                                                 HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        List<Map<String, Object>> created = new ArrayList<>();
        for (Object rawItem : objectList(payload.get("items"))) {
            if (rawItem instanceof Map<?, ?> item) {
                Map<String, Object> merged = new LinkedHashMap<>(payload);
                merged.putAll(toStringObjectMap(item));
                created.add(appendAssetItem(library, merged));
            }
        }
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "items", created);
    }

    @PatchMapping("/asset-library/items/{id}")
    public Map<String, Object> updateAssetItem(@PathVariable String id,
                                                @RequestBody Map<String, Object> payload,
                                                HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> item = requireAssetItem(assetLibraryFromState(state), id);
        String name = textValue(payload.get("name"));
        if (!name.isBlank()) item.put("name", limitText(name, 200));
        if (payload.containsKey("tags")) item.put("tags", normalizedTags(payload.get("tags")));
        if (payload.containsKey("description")) item.put("description", limitText(textValue(payload.get("description")), 2000));
        if (payload.containsKey("cover_url")) item.put("cover_url", optionalAssetUrl(payload.get("cover_url")));
        if (payload.containsKey("template_scope")) item.put("template_scope", normalizeTemplateScope(payload.get("template_scope")));
        if (payload.containsKey("template_type")) item.put("template_scope", normalizeTemplateScope(payload.get("template_type")));
        item.put("updated_at", System.currentTimeMillis());
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "item", item);
    }

    @PostMapping("/asset-library/items/organize")
    public Map<String, Object> organizeAssetItems(@RequestBody Map<String, Object> payload,
                                                   HttpServletRequest request) {
        Set<String> ids = objectList(payload.get("ids")).stream()
                .map(this::textValue)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) throw new IllegalArgumentException("请选择至少一个素材");

        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        Map<String, Object> targetLibrary = requireAssetLibrary(library, textValue(payload.get("library_id")));
        String targetCategoryId = textValue(payload.get("category_id"));
        Map<String, Object> targetCategory = mapList(targetLibrary, "categories").stream()
                .filter(category -> targetCategoryId.equals(textValue(category.get("id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("目标素材分组不存在"));

        List<Map<String, Object>> moved = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> item = detachAssetItem(library, id);
            if (item == null) continue;
            item.put("type", targetCategory.get("type"));
            if (payload.containsKey("tags")) {
                item.put("tags", boolValue(payload.get("merge_tags"))
                        ? mergeAssetTags(item.get("tags"), payload.get("tags"))
                        : normalizedTags(payload.get("tags")));
            }
            item.put("updated_at", System.currentTimeMillis());
            mapList(targetCategory, "items").add(item);
            moved.add(item);
        }
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "items", moved, "count", moved.size());
    }

    @PostMapping("/asset-library/items/delete")
    public Map<String, Object> deleteAssetItems(@RequestBody Map<String, Object> payload,
                                                 HttpServletRequest request) {
        Set<String> ids = objectList(payload.get("ids")).stream()
                .map(this::textValue)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        boolean deleteSource = boolValue(payload.get("delete_source"));
        List<String> removedIds = new ArrayList<>();
        List<String> sourceDeletedIds = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> item = detachAssetItem(library, id);
            if (item == null) continue;
            removedIds.add(id);
            if (deleteSource && deleteOwnedCanvasAsset(textValue(item.get("url")))) sourceDeletedIds.add(id);
        }
        saveLibraryState(row, state);
        return Map.of(
                "library", assetLibraryFromState(state),
                "removed_ids", removedIds,
                "source_deleted_ids", sourceDeletedIds,
                "source_delete_requested", deleteSource
        );
    }

    @GetMapping("/asset-library/duplicates")
    public Map<String, Object> getDuplicateAssetItems(HttpServletRequest request) {
        Map<String, Object> library = assetLibraryState(request);
        Map<String, List<Map<String, Object>>> candidates = new LinkedHashMap<>();
        for (Map<String, Object> assetLibrary : mapList(library, "libraries")) {
            for (Map<String, Object> category : mapList(assetLibrary, "categories")) {
                for (Map<String, Object> item : mapList(category, "items")) {
                    String fingerprint = assetFingerprint(item);
                    if (fingerprint.isBlank()) continue;
                    Map<String, Object> copy = new LinkedHashMap<>(item);
                    copy.put("library_id", assetLibrary.get("id"));
                    copy.put("category_id", category.get("id"));
                    candidates.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(copy);
                }
            }
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        candidates.forEach((fingerprint, items) -> {
            if (items.size() > 1) groups.add(Map.of("fingerprint", fingerprint, "items", items, "count", items.size()));
        });
        return Map.of("groups", groups, "strategy", "checksum_or_exact_url", "count", groups.size());
    }

    @PatchMapping("/asset-library/categories/{id}")
    public Map<String, Object> updateAssetCategory(@PathVariable String id,
                                                    @RequestBody Map<String, Object> payload,
                                                    HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> category = requireAssetCategory(assetLibraryFromState(state), id);
        String name = textValue(payload.get("name"));
        if (!name.isBlank()) category.put("name", limitText(name, 100));
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "category", category);
    }

    @DeleteMapping("/asset-library/items/{id}")
    public Map<String, Object> deleteAssetItem(@PathVariable String id,
                                               @RequestParam(value = "delete_source", defaultValue = "false") boolean deleteSource,
                                               HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> item = requireAssetItem(assetLibraryFromState(state), id);
        if (!removeAssetItem(assetLibraryFromState(state), id)) throw new RuntimeException("素材不存在");
        boolean sourceDeleted = deleteSource && deleteOwnedCanvasAsset(textValue(item.get("url")));
        saveLibraryState(row, state);
        return Map.of(
                "library", assetLibraryFromState(state),
                "source_delete_requested", deleteSource,
                "source_deleted", sourceDeleted
        );
    }

    @DeleteMapping("/asset-library/categories/{id}")
    public Map<String, Object> deleteAssetCategory(@PathVariable String id, HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        boolean removed = false;
        for (Map<String, Object> target : mapList(library, "libraries")) {
            List<Map<String, Object>> categories = mapList(target, "categories");
            if (categories.size() > 1 && categories.removeIf(category -> id.equals(textValue(category.get("id"))))) {
                removed = true;
                break;
            }
        }
        if (!removed) throw new RuntimeException("不能删除最后一个素材分组或分组不存在");
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state));
    }

    @DeleteMapping("/asset-library/libraries/{id}")
    public Map<String, Object> deleteAssetLibrary(@PathVariable String id, HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        List<Map<String, Object>> libraries = mapList(library, "libraries");
        if (libraries.size() <= 1 || !libraries.removeIf(item -> id.equals(textValue(item.get("id"))))) {
            throw new RuntimeException("至少保留一个素材库");
        }
        if (id.equals(textValue(library.get("active_library_id")))) library.put("active_library_id", libraries.get(0).get("id"));
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state));
    }

    @PostMapping("/asset-library/workflows/upload")
    public Map<String, Object> uploadWorkflowAssets(@RequestParam("files") List<MultipartFile> files,
                                                     @RequestParam(value = "library_id", required = false) String libraryId,
                                                     @RequestParam(value = "category_id", required = false) String categoryId,
                                                     HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = assetLibraryFromState(state);
        List<Map<String, Object>> created = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> uploaded = uploadFile(file);
            Map<String, Object> itemPayload = new LinkedHashMap<>();
            itemPayload.put("library_id", libraryId);
            itemPayload.put("category_id", categoryId);
            itemPayload.put("url", uploaded.get("url"));
            itemPayload.put("name", uploaded.get("name"));
            itemPayload.put("kind", "workflow");
            created.add(appendAssetItem(library, itemPayload));
        }
        saveLibraryState(row, state);
        return Map.of("library", assetLibraryFromState(state), "items", created);
    }

    @GetMapping("/legacy/asset-library-placeholder")
    public Map<String, Object> assetLibrary() {
        return Map.of("library", emptyAssetLibrary());
    }

    @PostMapping({"/legacy/asset-library/items", "/legacy/asset-library/items/batch", "/legacy/asset-library/categories", "/legacy/asset-library/libraries"})
    public Map<String, Object> mutateAssetLibrary() {
        return assetLibrary();
    }

    @DeleteMapping({"/legacy/asset-library/items/{id}", "/legacy/asset-library/categories/{id}", "/legacy/asset-library/libraries/{id}"})
    public Map<String, Object> deleteAssetLibraryItem() {
        return assetLibrary();
    }

    @GetMapping("/local-assets")
    public Map<String, Object> getLocalAssets(HttpServletRequest request) {
        return localAssetsResponse(libraryState(request));
    }

    @PostMapping("/local-assets/upload")
    public Map<String, Object> uploadLocalAssets(@RequestParam("files") List<MultipartFile> files,
                                                  @RequestParam(value = "folder", required = false) String folder,
                                                  HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        List<Map<String, Object>> created = new ArrayList<>();
        for (MultipartFile file : files) {
            created.add(appendLocalAsset(state, folder, uploadFile(file)));
        }
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("files", created);
        return response;
    }

    @PostMapping("/local-assets/import-urls")
    public Map<String, Object> importLocalAssetUrls(@RequestBody Map<String, Object> payload,
                                                     HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        List<Map<String, Object>> created = new ArrayList<>();
        for (Object rawItem : objectList(payload.get("items"))) {
            if (!(rawItem instanceof Map<?, ?> item)) continue;
            Map<String, Object> value = toStringObjectMap(item);
            String url = textValue(value.get("url"));
            if (url.isBlank()) continue;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("url", normalizeInputUrl(url));
            normalized.put("name", firstNonBlank(textValue(value.get("name")), "素材"));
            normalized.put("kind", firstNonBlank(textValue(value.get("kind")), mediaKind(textValue(value.get("name")), url)));
            created.add(appendLocalAsset(state, textValue(payload.get("folder")), normalized));
        }
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("files", created);
        response.put("count", created.size());
        return response;
    }

    @PostMapping("/local-assets/folders")
    public Map<String, Object> createLocalAssetFolder(@RequestBody Map<String, Object> payload,
                                                       HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> folder = appendLocalFolder(state, textValue(payload.get("parent")), textValue(payload.get("name")));
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("folder", folder);
        return response;
    }

    @PatchMapping("/local-assets/folders")
    public Map<String, Object> renameLocalAssetFolder(@RequestBody Map<String, Object> payload,
                                                       HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> folder = requireLocalFolder(state, textValue(payload.get("path")));
        String name = textValue(payload.get("name"));
        if (name.isBlank()) throw new IllegalArgumentException("文件夹名称不能为空");
        folder.put("name", limitText(name, 100));
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("folder", folder);
        return response;
    }

    @PatchMapping("/local-assets/items")
    public Map<String, Object> renameLocalAsset(@RequestBody Map<String, Object> payload,
                                                HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> item = requireLocalAsset(state, textValue(payload.get("path")));
        String name = textValue(payload.get("name"));
        if (name.isBlank()) throw new IllegalArgumentException("素材名称不能为空");
        String oldPath = textValue(item.get("file"));
        item.put("name", limitText(name, 200));
        if (payload.containsKey("tags")) item.put("tags", normalizedTags(payload.get("tags")));
        if (payload.containsKey("description")) item.put("description", limitText(textValue(payload.get("description")), 2000));
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("item", item);
        response.put("old_path", oldPath);
        return response;
    }

    @PostMapping("/local-assets/items/organize")
    public Map<String, Object> organizeLocalAssets(@RequestBody Map<String, Object> payload,
                                                    HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Set<String> ids = objectList(payload.get("ids")).stream()
                .map(this::textValue)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) throw new IllegalArgumentException("请选择至少一个素材");
        String folder = textValue(payload.get("folder"));
        if (!folder.isBlank()) requireLocalFolder(state, folder);
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Map<String, Object> item : localAssetItems(state)) {
            if (!ids.contains(textValue(item.get("id"))) && !ids.contains(textValue(item.get("file")))) continue;
            item.put("folder", folder);
            if (payload.containsKey("tags")) {
                item.put("tags", boolValue(payload.get("merge_tags"))
                        ? mergeAssetTags(item.get("tags"), payload.get("tags"))
                        : normalizedTags(payload.get("tags")));
            }
            if (payload.containsKey("description")) item.put("description", limitText(textValue(payload.get("description")), 2000));
            item.put("updated_at", System.currentTimeMillis());
            updated.add(item);
        }
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("items", updated);
        response.put("count", updated.size());
        return response;
    }

    @GetMapping("/local-assets/duplicates")
    public Map<String, Object> getDuplicateLocalAssets(HttpServletRequest request) {
        Map<String, List<Map<String, Object>>> candidates = new LinkedHashMap<>();
        for (Map<String, Object> item : localAssetItems(libraryState(request))) {
            String fingerprint = assetFingerprint(item);
            if (!fingerprint.isBlank()) candidates.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(item);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        candidates.forEach((fingerprint, items) -> {
            if (items.size() > 1) groups.add(Map.of("fingerprint", fingerprint, "items", items, "count", items.size()));
        });
        return Map.of("groups", groups, "strategy", "checksum_or_exact_url", "count", groups.size());
    }

    @PostMapping("/local-assets/delete")
    public Map<String, Object> deleteLocalAssets(@RequestBody Map<String, Object> payload,
                                                  HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Set<String> names = objectList(payload.get("names")).stream().map(this::textValue).collect(Collectors.toSet());
        List<Map<String, Object>> items = localAssetItems(state);
        boolean deleteSource = boolValue(payload.get("delete_source"));
        List<Map<String, Object>> removed = items.stream()
                .filter(item -> names.contains(textValue(item.get("id"))) || names.contains(textValue(item.get("file"))))
                .toList();
        List<String> deleted = removed.stream().map(item -> textValue(item.get("file"))).toList();
        List<String> sourceDeleted = removed.stream()
                .filter(item -> deleteSource && deleteOwnedCanvasAsset(textValue(item.get("url"))))
                .map(item -> textValue(item.get("file")))
                .toList();
        items.removeIf(item -> names.contains(textValue(item.get("id"))) || names.contains(textValue(item.get("file"))));
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>(localAssetsResponse(state));
        response.put("deleted", deleted);
        response.put("source_deleted", sourceDeleted);
        response.put("source_delete_requested", deleteSource);
        return response;
    }

    @GetMapping("/legacy/local-assets-placeholder")
    public Map<String, Object> localAssets() {
        return Map.of("items", List.of(), "tree", Map.of("id", "__root__", "name", "全部上传", "items", List.of(), "children", List.of()));
    }

    @GetMapping("/prompt-libraries")
    public Map<String, Object> getPromptLibraries(HttpServletRequest request) {
        return Map.of("library", promptLibraryState(request));
    }

    @PostMapping("/prompt-libraries")
    public Map<String, Object> createPromptLibrary(@RequestBody Map<String, Object> payload,
                                                    HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = promptLibraryFromState(state);
        Map<String, Object> created = newPromptLibrary(firstNonBlank(textValue(payload.get("name")), "我的提示词库"));
        mapList(library, "libraries").add(created);
        library.put("active_library_id", created.get("id"));
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state), "library_item", created);
    }

    @PostMapping("/prompt-libraries/items")
    public Map<String, Object> createPromptLibraryItem(@RequestBody Map<String, Object> payload,
                                                        HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> item = appendPromptItem(promptLibraryFromState(state), payload);
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state), "item", item);
    }

    @PatchMapping("/prompt-libraries/items/{id}")
    public Map<String, Object> updatePromptLibraryItem(@PathVariable String id,
                                                        @RequestBody Map<String, Object> payload,
                                                        HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> item = requirePromptItem(promptLibraryFromState(state), id);
        for (String key : List.of("name", "category", "scene", "positive", "negative")) {
            if (payload.containsKey(key)) item.put(key, limitText(textValue(payload.get(key)), key.equals("positive") || key.equals("negative") ? 8000 : 300));
        }
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state), "item", item);
    }

    @DeleteMapping("/prompt-libraries/items/{id}")
    public Map<String, Object> deletePromptLibraryItem(@PathVariable String id, HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        if (!removePromptItem(promptLibraryFromState(state), id)) throw new RuntimeException("提示词不存在");
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state));
    }

    @PostMapping("/prompt-libraries/categories")
    public Map<String, Object> createPromptCategory(@RequestBody Map<String, Object> payload,
                                                     HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = promptLibraryFromState(state);
        Map<String, Object> target = requirePromptLibrary(library, textValue(payload.get("library_id")));
        requireEditablePromptLibrary(target);
        Map<String, Object> category = newPromptCategory(firstNonBlank(textValue(payload.get("name")), "未分类"));
        mapList(target, "categories").add(category);
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state), "category", category);
    }

    @PatchMapping("/prompt-libraries/categories/{id}")
    public Map<String, Object> updatePromptCategory(@PathVariable String id,
                                                     @RequestBody Map<String, Object> payload,
                                                     HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> category = requirePromptCategory(promptLibraryFromState(state), id);
        String name = textValue(payload.get("name"));
        if (name.isBlank()) throw new IllegalArgumentException("分类名称不能为空");
        category.put("name", limitText(name, 100));
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state), "category", category);
    }

    @DeleteMapping("/prompt-libraries/categories/{id}")
    public Map<String, Object> deletePromptCategory(@PathVariable String id, HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = promptLibraryFromState(state);
        if (!removePromptCategory(library, id)) throw new RuntimeException("提示词分类不存在");
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state));
    }

    @DeleteMapping("/prompt-libraries/{id}")
    public Map<String, Object> deletePromptLibrary(@PathVariable String id, HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> library = promptLibraryFromState(state);
        List<Map<String, Object>> libraries = mapList(library, "libraries");
        if ("system".equals(id) || libraries.stream().filter(item -> !boolValue(item.get("readonly"))).count() <= 1
                || !libraries.removeIf(item -> id.equals(textValue(item.get("id"))))) {
            throw new RuntimeException("至少保留一个我的提示词库");
        }
        if (id.equals(textValue(library.get("active_library_id")))) library.put("active_library_id", "mine");
        saveLibraryState(row, state);
        return Map.of("library", promptLibraryFromState(state));
    }

    @GetMapping("/legacy/prompt-libraries-placeholder")
    public Map<String, Object> promptLibraries() {
        return Map.of("library", Map.of(
                "active_library_id", "system",
                "libraries", List.of(Map.of("id", "system", "name", "系统提示词库", "readonly", true, "items", List.of()))
        ));
    }

    @PostMapping({"/legacy/prompt-libraries", "/legacy/prompt-libraries/items", "/legacy/prompt-libraries/items/delete", "/legacy/prompt-libraries/categories"})
    public Map<String, Object> mutatePromptLibraries() {
        return promptLibraries();
    }

    @DeleteMapping({"/legacy/prompt-libraries/{id}", "/legacy/prompt-libraries/items/{id}", "/legacy/prompt-libraries/categories/{id}"})
    public Map<String, Object> deletePromptLibraryItem() {
        return promptLibraries();
    }

    @GetMapping("/smart-canvas/prompt-templates")
    public Map<String, Object> getSmartCanvasPromptTemplates(HttpServletRequest request) {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (Map<String, Object> library : mapList(promptLibraryState(request), "libraries")) {
            for (Map<String, Object> item : mapList(library, "items")) {
                Map<String, Object> template = new LinkedHashMap<>(item);
                template.put("libraryId", library.get("id"));
                templates.add(template);
            }
        }
        return Map.of("templates", templates, "source", "ai-project");
    }

    @PostMapping(value = "/canvas-workflows/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportCanvasWorkflow(@RequestBody Map<String, Object> payload) {
        WorkflowArchive archive = createWorkflowArchive(payload);
        String filename = workflowFilename(textValue(payload.get("filename")));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-AI-Canvas-Resources-Found", String.valueOf(archive.sourceCount()))
                .header("X-AI-Canvas-Resources-Packed", String.valueOf(archive.packedCount()))
                .header("X-AI-Canvas-Resources-Skipped", String.valueOf(archive.skippedCount()))
                .contentLength(archive.bytes().length)
                .body(archive.bytes());
    }

    @PostMapping("/canvas-workflows/export-to-library")
    public Map<String, Object> exportCanvasWorkflowToLibrary(@RequestBody Map<String, Object> payload,
                                                              HttpServletRequest request) {
        String filename = workflowFilename(textValue(payload.get("filename")));
        WorkflowArchive archive = createWorkflowArchive(payload);
        Map<String, Object> uploaded = uploadWorkflowBytes(archive.bytes(), filename, "application/zip");
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        Map<String, Object> itemPayload = new LinkedHashMap<>();
        itemPayload.put("library_id", payload.get("library_id"));
        itemPayload.put("category_id", payload.get("category_id"));
        itemPayload.put("url", uploaded.get("url"));
        itemPayload.put("name", firstNonBlank(textValue(payload.get("name")), filename.replaceFirst("(?i)\\.zip$", "")));
        itemPayload.put("kind", "workflow");
        for (String key : List.of("tags", "description", "cover_url", "template_scope", "template_type", "scope")) {
            if (payload.containsKey(key)) itemPayload.put(key, payload.get(key));
        }
        Map<String, Object> item = appendAssetItem(assetLibraryFromState(state), itemPayload);
        saveLibraryState(row, state);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("library", assetLibraryFromState(state));
        response.put("item", item);
        response.put("resource_summary", workflowResourceSummary(archive.sourceCount(), archive.packedCount(), archive.skippedCount()));
        return response;
    }

    @PostMapping("/canvas-workflows/import")
    public Map<String, Object> importCanvasWorkflow(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择工作流 JSON 或 ZIP 文件");
        String name = firstNonBlank(file.getOriginalFilename(), "workflow.json");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > WORKFLOW_ARCHIVE_MAX_BYTES) throw new IllegalArgumentException("工作流文件不能超过 220MB");
            WorkflowImport imported = name.toLowerCase(Locale.ROOT).endsWith(".zip")
                    ? importWorkflowArchive(bytes)
                    : new WorkflowImport(readWorkflowJson(bytes), 0, 0, 0);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("workflow", imported.workflow());
            response.put("resource_summary", workflowResourceSummary(imported.sourceCount(), imported.restoredCount(), imported.skippedCount()));
            return response;
        } catch (IOException e) {
            throw new RuntimeException("读取工作流失败: " + e.getMessage(), e);
        }
    }

    @GetMapping("/legacy/smart-canvas/prompt-templates-placeholder")
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

    private CanvasProject libraryRow(HttpServletRequest request) {
        return libraryRow(request, ownedRows(request));
    }

    private CanvasProject libraryRow(HttpServletRequest request, List<CanvasProject> rows) {
        Optional<CanvasProject> existing = rows.stream().filter(this::isLibraryRow).findFirst();
        if (existing.isPresent()) return existing.get();
        CanvasProject row = new CanvasProject();
        row.setOperator(currentOperator(request));
        row.setShopName(currentShopName(request));
        row.setProjectName(LIBRARY_PROJECT_NAME);
        row.setMetaJson(writeJson(Map.of("kind", LIBRARY_KIND)));
        row.setSnapshotJson(writeJson(defaultLibraryState()));
        return canvasProjectRepository.save(row);
    }

    private Map<String, Object> libraryState(HttpServletRequest request) {
        CanvasProject row = libraryRow(request);
        Map<String, Object> state = libraryState(row);
        saveLibraryState(row, state);
        return state;
    }

    private Map<String, Object> libraryState(CanvasProject row) {
        Map<String, Object> state = readMap(row.getSnapshotJson());
        if (!(state.get("asset_library") instanceof Map<?, ?>)) state.put("asset_library", newAssetLibrary("我的素材库"));
        if (!(state.get("prompt_library") instanceof Map<?, ?>)) state.put("prompt_library", defaultPromptLibrary());
        if (!(state.get("local_assets") instanceof Map<?, ?>)) state.put("local_assets", defaultLocalAssets());
        assetLibraryFromState(state);
        promptLibraryFromState(state);
        localAssetsState(state);
        return state;
    }

    private void saveLibraryState(CanvasProject row, Map<String, Object> state) {
        row.setProjectName(LIBRARY_PROJECT_NAME);
        row.setMetaJson(writeJson(Map.of("kind", LIBRARY_KIND)));
        row.setSnapshotJson(writeJson(state));
        canvasProjectRepository.save(row);
    }

    private Map<String, Object> defaultLibraryState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("asset_library", newAssetLibrary("我的素材库"));
        state.put("prompt_library", defaultPromptLibrary());
        state.put("local_assets", defaultLocalAssets());
        return state;
    }

    private boolean isLibraryRow(CanvasProject row) {
        return LIBRARY_PROJECT_NAME.equals(row.getProjectName()) || LIBRARY_KIND.equals(textValue(readMeta(row).get("kind")));
    }

    private Map<String, Object> assetLibraryState(HttpServletRequest request) {
        return assetLibraryFromState(libraryState(request));
    }

    private Map<String, Object> assetLibraryFromState(Map<String, Object> state) {
        Map<String, Object> library = objectMap(state.get("asset_library"));
        if (library.isEmpty()) library = newAssetLibrary("我的素材库");
        state.put("asset_library", library);
        List<Map<String, Object>> libraries = mapList(library, "libraries");
        if (libraries.isEmpty()) libraries.add(newAssetLibraryEntry("我的素材库"));
        for (Map<String, Object> item : libraries) ensureAssetLibraryShape(item);
        String activeId = textValue(library.get("active_library_id"));
        if (libraries.stream().noneMatch(item -> activeId.equals(textValue(item.get("id"))))) {
            library.put("active_library_id", libraries.get(0).get("id"));
        }
        Map<String, Object> active = requireAssetLibrary(library, textValue(library.get("active_library_id")));
        library.put("categories", mapList(active, "categories"));
        library.put("updated_at", System.currentTimeMillis());
        return library;
    }

    private Map<String, Object> newAssetLibrary(String name) {
        Map<String, Object> library = newAssetLibraryEntry(name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active_library_id", library.get("id"));
        result.put("libraries", new ArrayList<>(List.of(library)));
        result.put("categories", library.get("categories"));
        return result;
    }

    private Map<String, Object> newAssetLibraryEntry(String name) {
        Map<String, Object> library = new LinkedHashMap<>();
        library.put("id", "lib_" + UUID.randomUUID());
        library.put("name", limitText(name, 100));
        library.put("categories", new ArrayList<>(List.of(
                newAssetCategory("图片素材", "image"),
                newAssetCategory("工作流", "workflow")
        )));
        return library;
    }

    private void ensureAssetLibraryShape(Map<String, Object> library) {
        if (textValue(library.get("id")).isBlank()) library.put("id", "lib_" + UUID.randomUUID());
        if (textValue(library.get("name")).isBlank()) library.put("name", "我的素材库");
        List<Map<String, Object>> categories = mapList(library, "categories");
        if (categories.stream().noneMatch(item -> "image".equals(textValue(item.get("type"))))) {
            categories.add(newAssetCategory("图片素材", "image"));
        }
        if (categories.stream().noneMatch(item -> "workflow".equals(textValue(item.get("type"))))) {
            categories.add(newAssetCategory("工作流", "workflow"));
        }
        categories.forEach(category -> {
            if (textValue(category.get("id")).isBlank()) category.put("id", "cat_" + UUID.randomUUID());
            if (textValue(category.get("name")).isBlank()) category.put("name", "素材分组");
            if (!"workflow".equals(textValue(category.get("type")))) category.put("type", "image");
            mapList(category, "items");
        });
    }

    private Map<String, Object> newAssetCategory(String name, String type) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", "cat_" + UUID.randomUUID());
        category.put("name", limitText(name, 100));
        category.put("type", type);
        category.put("items", new ArrayList<>());
        return category;
    }

    private Map<String, Object> requireAssetLibrary(Map<String, Object> library, String requestedId) {
        List<Map<String, Object>> libraries = mapList(library, "libraries");
        String id = firstNonBlank(requestedId, textValue(library.get("active_library_id")), textValue(libraries.isEmpty() ? null : libraries.get(0).get("id")));
        return libraries.stream().filter(item -> id.equals(textValue(item.get("id")))).findFirst()
                .orElseThrow(() -> new RuntimeException("素材库不存在"));
    }

    private Map<String, Object> requireAssetCategory(Map<String, Object> library, String categoryId) {
        return mapList(library, "libraries").stream()
                .flatMap(item -> mapList(item, "categories").stream())
                .filter(item -> categoryId.equals(textValue(item.get("id"))))
                .findFirst().orElseThrow(() -> new RuntimeException("素材分组不存在"));
    }

    private Map<String, Object> appendAssetItem(Map<String, Object> library, Map<String, Object> payload) {
        Map<String, Object> target = requireAssetLibrary(library, textValue(payload.get("library_id")));
        List<Map<String, Object>> categories = mapList(target, "categories");
        String kind = firstNonBlank(textValue(payload.get("kind")), textValue(payload.get("type")));
        String requestedCategory = textValue(payload.get("category_id"));
        Map<String, Object> category = categories.stream().filter(item -> requestedCategory.equals(textValue(item.get("id")))).findFirst().orElse(null);
        if (category == null) {
            String preferredType = "workflow".equalsIgnoreCase(kind) ? "workflow" : "image";
            category = categories.stream().filter(item -> preferredType.equals(textValue(item.get("type")))).findFirst()
                    .orElseThrow(() -> new RuntimeException("素材分组不存在"));
        }
        String rawUrl = textValue(payload.get("url"));
        if (rawUrl.isBlank()) throw new IllegalArgumentException("素材地址不能为空");
        String url = normalizeInputUrl(rawUrl);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "asset_" + UUID.randomUUID());
        item.put("url", url);
        item.put("name", limitText(firstNonBlank(textValue(payload.get("name")), nameFromUrl(url), "素材"), 200));
        item.put("kind", firstNonBlank(kind, "workflow".equals(textValue(category.get("type"))) ? "workflow" : mediaKind(textValue(item.get("name")), url)));
        item.put("type", category.get("type"));
        item.put("created_at", System.currentTimeMillis());
        for (String key : List.of("thumbnail", "classification", "asset_uris", "width", "height", "duration", "checksum", "size")) {
            if (payload.containsKey(key)) item.put(key, payload.get(key));
        }
        item.put("tags", normalizedTags(payload.get("tags")));
        item.put("description", limitText(textValue(payload.get("description")), 2000));
        item.put("cover_url", optionalAssetUrl(payload.get("cover_url")));
        if ("workflow".equals(item.get("kind"))) {
            item.put("template_scope", normalizeTemplateScope(firstNonBlank(
                    textValue(payload.get("template_scope")),
                    textValue(payload.get("template_type")),
                    textValue(payload.get("scope"))
            )));
        }
        mapList(category, "items").add(item);
        return item;
    }

    private Map<String, Object> requireAssetItem(Map<String, Object> library, String itemId) {
        return mapList(library, "libraries").stream()
                .flatMap(item -> mapList(item, "categories").stream())
                .flatMap(item -> mapList(item, "items").stream())
                .filter(item -> itemId.equals(textValue(item.get("id"))))
                .findFirst().orElseThrow(() -> new RuntimeException("素材不存在"));
    }

    private boolean removeAssetItem(Map<String, Object> library, String itemId) {
        for (Map<String, Object> target : mapList(library, "libraries")) {
            for (Map<String, Object> category : mapList(target, "categories")) {
                if (mapList(category, "items").removeIf(item -> itemId.equals(textValue(item.get("id"))))) return true;
            }
        }
        return false;
    }

    private Map<String, Object> detachAssetItem(Map<String, Object> library, String itemId) {
        for (Map<String, Object> target : mapList(library, "libraries")) {
            for (Map<String, Object> category : mapList(target, "categories")) {
                List<Map<String, Object>> items = mapList(category, "items");
                for (int index = 0; index < items.size(); index++) {
                    if (itemId.equals(textValue(items.get(index).get("id")))) return items.remove(index);
                }
            }
        }
        return null;
    }

    private List<String> normalizedTags(Object rawTags) {
        List<String> raw = new ArrayList<>();
        if (rawTags instanceof String text) {
            Collections.addAll(raw, text.split("[,，\\n]"));
        } else {
            objectList(rawTags).forEach(value -> raw.add(textValue(value)));
        }
        return raw.stream()
                .map(value -> limitText(value, 48))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(20)
                .toList();
    }

    private List<String> mergeAssetTags(Object existing, Object incoming) {
        List<String> merged = new ArrayList<>(normalizedTags(existing));
        merged.addAll(normalizedTags(incoming));
        return normalizedTags(merged);
    }

    private String optionalAssetUrl(Object value) {
        String raw = textValue(value);
        return raw.isBlank() ? "" : normalizeInputUrl(raw);
    }

    private String normalizeTemplateScope(Object value) {
        String scope = textValue(value).trim().toLowerCase(Locale.ROOT);
        return "full_canvas".equals(scope) || "canvas".equals(scope) ? "full_canvas" : "selected_workflow";
    }

    private String assetFingerprint(Map<String, Object> item) {
        String checksum = textValue(item.get("checksum"));
        if (!checksum.isBlank()) return "checksum:" + textValue(item.get("kind")) + ":" + checksum;
        String url = textValue(item.get("url"));
        return url.isBlank() ? "" : "url:" + textValue(item.get("kind")) + ":" + url;
    }

    private boolean deleteOwnedCanvasAsset(String sourceUrl) {
        String publicHost = appProperties.getOss() == null ? "" : textValue(appProperties.getOss().getInputPublicHost()).replaceAll("/+$", "");
        String prefix = publicHost + "/AI_CANVAS/";
        if (publicHost.isBlank() || !sourceUrl.startsWith(prefix)) return false;
        String objectName = sourceUrl.substring(publicHost.length() + 1);
        if (!objectName.startsWith("AI_CANVAS/")) return false;
        try {
            ossService.getOssClient().deleteObject(appProperties.getOss().getInputBucket(), objectName);
            return true;
        } catch (Exception error) {
            log.warn("Cannot delete AI canvas source object {}: {}", objectName, error.getMessage());
            return false;
        }
    }

    private Map<String, Object> localAssetsState(Map<String, Object> state) {
        Map<String, Object> local = objectMap(state.get("local_assets"));
        if (local.isEmpty()) local = defaultLocalAssets();
        state.put("local_assets", local);
        mapList(local, "folders");
        mapList(local, "items");
        return local;
    }

    private Map<String, Object> defaultLocalAssets() {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("folders", new ArrayList<>());
        local.put("items", new ArrayList<>());
        return local;
    }

    private List<Map<String, Object>> localAssetItems(Map<String, Object> state) {
        return mapList(localAssetsState(state), "items");
    }

    private Map<String, Object> localAssetsResponse(Map<String, Object> state) {
        Map<String, Object> local = localAssetsState(state);
        List<Map<String, Object>> items = mapList(local, "items");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "__root__");
        root.put("path", "__root__");
        root.put("name", "全部素材");
        root.put("items", items);
        root.put("children", localAssetFolderChildren(local, "__root__"));
        return Map.of("items", items, "tree", root);
    }

    private List<Map<String, Object>> localAssetFolderChildren(Map<String, Object> local, String parent) {
        List<Map<String, Object>> children = new ArrayList<>();
        for (Map<String, Object> folder : mapList(local, "folders")) {
            if (!parent.equals(firstNonBlank(textValue(folder.get("parent")), "__root__"))) continue;
            String id = textValue(folder.get("id"));
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", id);
            node.put("path", id);
            node.put("name", textValue(folder.get("name")));
            node.put("items", mapList(local, "items").stream()
                    .filter(item -> id.equals(textValue(item.get("folder")))).toList());
            node.put("children", localAssetFolderChildren(local, id));
            children.add(node);
        }
        children.sort(Comparator.comparing(item -> textValue(item.get("name")), String.CASE_INSENSITIVE_ORDER));
        return children;
    }

    private Map<String, Object> appendLocalFolder(Map<String, Object> state, String parent, String name) {
        Map<String, Object> local = localAssetsState(state);
        String safeName = limitText(name, 100);
        if (safeName.isBlank()) throw new IllegalArgumentException("文件夹名称不能为空");
        String parentId = firstNonBlank(parent, "__root__");
        if (!"__root__".equals(parentId)) requireLocalFolder(state, parentId);
        Map<String, Object> folder = new LinkedHashMap<>();
        folder.put("id", "local_folder_" + UUID.randomUUID());
        folder.put("path", folder.get("id"));
        folder.put("parent", parentId);
        folder.put("name", safeName);
        mapList(local, "folders").add(folder);
        return folder;
    }

    private Map<String, Object> requireLocalFolder(Map<String, Object> state, String folderId) {
        return mapList(localAssetsState(state), "folders").stream()
                .filter(item -> folderId.equals(textValue(item.get("id"))) || folderId.equals(textValue(item.get("path"))))
                .findFirst().orElseThrow(() -> new RuntimeException("本地素材文件夹不存在"));
    }

    private Map<String, Object> appendLocalAsset(Map<String, Object> state, String folder, Map<String, Object> uploaded) {
        String folderId = firstNonBlank(folder, "__root__");
        if (!"__root__".equals(folderId)) requireLocalFolder(state, folderId);
        String url = textValue(uploaded.get("url"));
        if (url.isBlank()) throw new IllegalArgumentException("素材地址不能为空");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "local_asset_" + UUID.randomUUID());
        item.put("file", item.get("id"));
        item.put("folder", folderId);
        item.put("url", normalizeInputUrl(url));
        item.put("name", limitText(firstNonBlank(textValue(uploaded.get("name")), nameFromUrl(url), "素材"), 200));
        item.put("kind", firstNonBlank(textValue(uploaded.get("kind")), mediaKind(textValue(item.get("name")), url)));
        item.put("created_at", System.currentTimeMillis());
        for (String key : List.of("thumbnail", "width", "height", "duration", "checksum", "size")) {
            if (uploaded.containsKey(key)) item.put(key, uploaded.get(key));
        }
        localAssetItems(state).add(item);
        return item;
    }

    private Map<String, Object> requireLocalAsset(Map<String, Object> state, String path) {
        return localAssetItems(state).stream()
                .filter(item -> path.equals(textValue(item.get("id"))) || path.equals(textValue(item.get("file"))))
                .findFirst().orElseThrow(() -> new RuntimeException("本地素材不存在"));
    }

    private Map<String, Object> promptLibraryState(HttpServletRequest request) {
        return promptLibraryFromState(libraryState(request));
    }

    private Map<String, Object> promptLibraryFromState(Map<String, Object> state) {
        Map<String, Object> library = objectMap(state.get("prompt_library"));
        if (library.isEmpty()) library = defaultPromptLibrary();
        state.put("prompt_library", library);
        List<Map<String, Object>> libraries = mapList(library, "libraries");
        if (libraries.stream().noneMatch(item -> "system".equals(textValue(item.get("id"))))) libraries.add(systemPromptLibrary());
        if (libraries.stream().noneMatch(item -> !boolValue(item.get("readonly")))) libraries.add(newPromptLibrary("我的提示词库"));
        libraries.forEach(this::ensurePromptLibraryShape);
        String activeId = textValue(library.get("active_library_id"));
        if (libraries.stream().noneMatch(item -> activeId.equals(textValue(item.get("id"))))) {
            library.put("active_library_id", libraries.stream().filter(item -> !boolValue(item.get("readonly"))).findFirst()
                    .map(item -> item.get("id")).orElse("system"));
        }
        library.put("updated_at", System.currentTimeMillis());
        return library;
    }

    private Map<String, Object> defaultPromptLibrary() {
        Map<String, Object> library = new LinkedHashMap<>();
        library.put("active_library_id", "mine");
        library.put("libraries", new ArrayList<>(List.of(systemPromptLibrary(), newPromptLibrary("我的提示词库", "mine"))));
        return library;
    }

    private Map<String, Object> systemPromptLibrary() {
        Map<String, Object> library = new LinkedHashMap<>();
        library.put("id", "system");
        library.put("name", "系统提示词库");
        library.put("readonly", true);
        library.put("categories", new ArrayList<>(List.of(newPromptCategory("通用", "general"))));
        library.put("items", new ArrayList<>());
        return library;
    }

    private Map<String, Object> newPromptLibrary(String name) {
        return newPromptLibrary(name, "prompt_lib_" + UUID.randomUUID());
    }

    private Map<String, Object> newPromptLibrary(String name, String id) {
        Map<String, Object> library = new LinkedHashMap<>();
        library.put("id", id);
        library.put("name", limitText(name, 100));
        library.put("readonly", false);
        library.put("categories", new ArrayList<>(List.of(newPromptCategory("默认分类", "custom"))));
        library.put("items", new ArrayList<>());
        return library;
    }

    private Map<String, Object> newPromptCategory(String name) {
        return newPromptCategory(name, "prompt_cat_" + UUID.randomUUID());
    }

    private Map<String, Object> newPromptCategory(String name, String id) {
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", id);
        category.put("name", limitText(name, 100));
        return category;
    }

    private void ensurePromptLibraryShape(Map<String, Object> library) {
        if (textValue(library.get("id")).isBlank()) library.put("id", "prompt_lib_" + UUID.randomUUID());
        if (textValue(library.get("name")).isBlank()) library.put("name", "我的提示词库");
        library.putIfAbsent("readonly", false);
        List<Map<String, Object>> categories = mapList(library, "categories");
        if (categories.isEmpty()) categories.add(newPromptCategory("默认分类", "custom"));
        categories.forEach(category -> {
            if (textValue(category.get("id")).isBlank()) category.put("id", "prompt_cat_" + UUID.randomUUID());
            if (textValue(category.get("name")).isBlank()) category.put("name", "默认分类");
        });
        mapList(library, "items").forEach(item -> {
            item.putIfAbsent("id", "prompt_" + UUID.randomUUID());
            item.putIfAbsent("category", categories.get(0).get("id"));
            item.putIfAbsent("name", "未命名提示词");
            item.putIfAbsent("positive", "");
            item.putIfAbsent("negative", "");
        });
    }

    private Map<String, Object> requirePromptLibrary(Map<String, Object> library, String requestedId) {
        List<Map<String, Object>> libraries = mapList(library, "libraries");
        String id = firstNonBlank(requestedId, textValue(library.get("active_library_id")), "mine");
        return libraries.stream().filter(item -> id.equals(textValue(item.get("id")))).findFirst()
                .orElseThrow(() -> new RuntimeException("提示词库不存在"));
    }

    private void requireEditablePromptLibrary(Map<String, Object> library) {
        if (boolValue(library.get("readonly"))) throw new RuntimeException("系统提示词库不可修改");
    }

    private Map<String, Object> appendPromptItem(Map<String, Object> library, Map<String, Object> payload) {
        Map<String, Object> target = requirePromptLibrary(library, textValue(payload.get("library_id")));
        requireEditablePromptLibrary(target);
        List<Map<String, Object>> categories = mapList(target, "categories");
        String requestedCategoryId = firstNonBlank(textValue(payload.get("category")), textValue(categories.get(0).get("id")));
        Map<String, Object> category = categories.stream()
                .filter(value -> requestedCategoryId.equals(textValue(value.get("id")))).findFirst().orElse(null);
        if (category == null) {
            category = newPromptCategory(requestedCategoryId);
            categories.add(category);
        }
        String categoryId = textValue(category.get("id"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "prompt_" + UUID.randomUUID());
        item.put("libraryId", target.get("id"));
        item.put("name", limitText(firstNonBlank(textValue(payload.get("name")), "未命名提示词"), 300));
        item.put("category", categoryId);
        item.put("scene", limitText(textValue(payload.get("scene")), 300));
        item.put("positive", limitText(textValue(payload.get("positive")), 8000));
        item.put("negative", limitText(textValue(payload.get("negative")), 8000));
        item.put("created_at", System.currentTimeMillis());
        mapList(target, "items").add(item);
        return item;
    }

    private Map<String, Object> requirePromptItem(Map<String, Object> library, String id) {
        for (Map<String, Object> target : mapList(library, "libraries")) {
            for (Map<String, Object> item : mapList(target, "items")) {
                if (id.equals(textValue(item.get("id")))) {
                    requireEditablePromptLibrary(target);
                    return item;
                }
            }
        }
        throw new RuntimeException("提示词不存在");
    }

    private boolean removePromptItem(Map<String, Object> library, String id) {
        for (Map<String, Object> target : mapList(library, "libraries")) {
            if (mapList(target, "items").stream().anyMatch(item -> id.equals(textValue(item.get("id"))))) {
                requireEditablePromptLibrary(target);
                return mapList(target, "items").removeIf(item -> id.equals(textValue(item.get("id"))));
            }
        }
        return false;
    }

    private Map<String, Object> requirePromptCategory(Map<String, Object> library, String id) {
        for (Map<String, Object> target : mapList(library, "libraries")) {
            for (Map<String, Object> category : mapList(target, "categories")) {
                if (id.equals(textValue(category.get("id")))) {
                    requireEditablePromptLibrary(target);
                    return category;
                }
            }
        }
        throw new RuntimeException("提示词分类不存在");
    }

    private boolean removePromptCategory(Map<String, Object> library, String id) {
        for (Map<String, Object> target : mapList(library, "libraries")) {
            List<Map<String, Object>> categories = mapList(target, "categories");
            if (categories.stream().noneMatch(item -> id.equals(textValue(item.get("id"))))) continue;
            requireEditablePromptLibrary(target);
            if (categories.size() <= 1) throw new RuntimeException("至少保留一个提示词分类");
            String fallback = textValue(categories.stream().filter(item -> !id.equals(textValue(item.get("id")))).findFirst().orElseThrow().get("id"));
            mapList(target, "items").forEach(item -> {
                if (id.equals(textValue(item.get("category")))) item.put("category", fallback);
            });
            return categories.removeIf(item -> id.equals(textValue(item.get("id"))));
        }
        return false;
    }

    private WorkflowArchive createWorkflowArchive(Map<String, Object> payload) {
        Map<String, Object> workflow = objectMapper.convertValue(payload == null ? Map.of() : payload,
                new TypeReference<LinkedHashMap<String, Object>>() {});
        Set<String> sourceUrls = new LinkedHashSet<>();
        collectWorkflowUrls(workflow, sourceUrls);
        Map<String, String> urlToArchivePath = new LinkedHashMap<>();
        Map<String, byte[]> resources = new LinkedHashMap<>();
        List<String> skippedSources = new ArrayList<>();
        int index = 1;
        for (String url : sourceUrls) {
            try {
                byte[] bytes = downloadWorkflowResource(url);
                if (bytes.length == 0) {
                    skippedSources.add(url);
                    continue;
                }
                String path = uniqueWorkflowResourcePath(nameFromUrl(url), index++, resources.keySet());
                resources.put(path, bytes);
                urlToArchivePath.put(url, path);
            } catch (Exception e) {
                skippedSources.add(url);
                log.debug("Skip non-exportable workflow resource: {}", url, e);
            }
        }
        Object rewritten = replaceWorkflowUrls(workflow, urlToArchivePath);
        List<Map<String, Object>> manifest = new ArrayList<>();
        urlToArchivePath.forEach((url, path) -> manifest.add(Map.of("path", path, "source_url", url)));
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("workflow", rewritten);
        bundle.put("resources", manifest);
        bundle.put("resource_summary", workflowResourceSummary(sourceUrls.size(), resources.size(), skippedSources.size()));
        bundle.put("format", "ai-canvas-workflow-bundle");
        bundle.put("version", 1);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            writeZipEntry(zip, "workflow.json", objectMapper.writeValueAsBytes(bundle));
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) writeZipEntry(zip, entry.getKey(), entry.getValue());
            zip.finish();
            return new WorkflowArchive(output.toByteArray(), sourceUrls.size(), resources.size(), skippedSources.size());
        } catch (IOException e) {
            throw new RuntimeException("打包工作流失败: " + e.getMessage(), e);
        }
    }

    private WorkflowImport importWorkflowArchive(byte[] archive) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0L;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.contains("../") || name.startsWith("/")) throw new IllegalArgumentException("工作流压缩包包含非法路径");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    total += read;
                    if (total > WORKFLOW_ARCHIVE_MAX_BYTES) throw new IllegalArgumentException("工作流压缩包解压后不能超过 220MB");
                    output.write(buffer, 0, read);
                }
                files.put(name, output.toByteArray());
            }
        }
        byte[] workflowBytes = files.remove("workflow.json");
        if (workflowBytes == null) {
            workflowBytes = files.entrySet().stream()
                    .filter(item -> item.getKey().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .map(Map.Entry::getValue).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("压缩包中没有工作流 JSON"));
        }
        Map<String, Object> root = readJsonObject(workflowBytes);
        Map<String, Object> workflow = root.get("workflow") instanceof Map<?, ?> map ? toStringObjectMap(map) : root;
        Map<String, String> archivePathToUrl = new LinkedHashMap<>();
        Map<String, String> archivePathToSourceUrl = new LinkedHashMap<>();
        List<Map<String, Object>> manifest = mapList(root, "resources");
        for (Map<String, Object> item : manifest) {
            String path = textValue(item.get("path"));
            String sourceUrl = textValue(item.get("source_url"));
            if (!path.isBlank() && !sourceUrl.isBlank()) archivePathToSourceUrl.put(path, sourceUrl);
        }
        Set<String> resourcePaths = new LinkedHashSet<>();
        if (manifest.isEmpty()) {
            files.keySet().stream()
                    .filter(path -> !path.toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(resourcePaths::add);
        } else {
            manifest.stream().map(item -> textValue(item.get("path")))
                    .filter(path -> !path.isBlank() && files.containsKey(path))
                    .forEach(resourcePaths::add);
        }
        int restoredCount = 0;
        int skippedCount = 0;
        for (String path : resourcePaths) {
            try {
                Map<String, Object> uploaded = uploadWorkflowBytes(files.get(path), path,
                        firstNonBlank(URLConnection.guessContentTypeFromName(path), "application/octet-stream"));
                archivePathToUrl.put(path, textValue(uploaded.get("url")));
                restoredCount++;
            } catch (Exception e) {
                skippedCount++;
                String sourceUrl = archivePathToSourceUrl.get(path);
                if (sourceUrl != null && !sourceUrl.isBlank()) archivePathToUrl.put(path, sourceUrl);
                log.warn("Skip non-importable workflow resource: {}", path, e);
            }
        }
        Object restored = replaceWorkflowUrls(workflow, archivePathToUrl);
        Map<String, Object> restoredWorkflow = restored instanceof Map<?, ?> map ? toStringObjectMap(map) : workflow;
        return new WorkflowImport(restoredWorkflow, resourcePaths.size(), restoredCount, skippedCount);
    }

    private Map<String, Object> workflowResourceSummary(int sourceCount, int completedCount, int skippedCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("found", sourceCount);
        summary.put("completed", completedCount);
        summary.put("skipped", skippedCount);
        return summary;
    }

    private Map<String, Object> readWorkflowJson(byte[] bytes) throws IOException {
        Map<String, Object> root = readJsonObject(bytes);
        return root.get("workflow") instanceof Map<?, ?> map ? toStringObjectMap(map) : root;
    }

    private Map<String, Object> readJsonObject(byte[] bytes) throws IOException {
        return objectMapper.readValue(bytes, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private byte[] downloadWorkflowResource(String sourceUrl) throws IOException {
        URI target = trustedMediaUri(sourceUrl);
        for (int redirect = 0; redirect <= MEDIA_PROXY_MAX_REDIRECTS; redirect++) {
            Request request = new Request.Builder().url(target.toString()).get().build();
            try (Response response = mediaProxyClient.newCall(request).execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) throw new IOException("媒体重定向地址为空");
                    target = trustedMediaUri(target.resolve(location).toString());
                    continue;
                }
                if (!response.isSuccessful()) throw new IOException("媒体下载失败，HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null) throw new IOException("媒体内容为空");
                long length = body.contentLength();
                if (length > KIE_MEDIA_UPLOAD_MAX_BYTES) throw new IOException("媒体超过 100MB");
                byte[] bytes = body.bytes();
                if (bytes.length > KIE_MEDIA_UPLOAD_MAX_BYTES) throw new IOException("媒体超过 100MB");
                return bytes;
            }
        }
        throw new IOException("媒体重定向次数过多");
    }

    private void collectWorkflowUrls(Object value, Set<String> urls) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if ("url".equalsIgnoreCase(String.valueOf(key)) && child instanceof String url
                        && (url.startsWith("http://") || url.startsWith("https://"))) {
                    urls.add(url);
                }
                collectWorkflowUrls(child, urls);
            });
        } else if (value instanceof List<?> list) {
            list.forEach(item -> collectWorkflowUrls(item, urls));
        }
    }

    private Object replaceWorkflowUrls(Object value, Map<String, String> replacements) {
        if (value instanceof String text) return replacements.getOrDefault(text, text);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put(String.valueOf(key), replaceWorkflowUrls(child, replacements)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(item -> replaceWorkflowUrls(item, replacements)).toList();
        return value;
    }

    private String uniqueWorkflowResourcePath(String sourceName, int index, Set<String> existing) {
        String extension = fileExtension(sourceName, "");
        String base = limitText(sourceName.replaceFirst("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9._-]", "_"), 80);
        if (base.isBlank() || "_".equals(base)) base = "resource";
        String path = "resources/" + base + "_" + index + extension;
        int duplicate = 2;
        while (existing.contains(path)) path = "resources/" + base + "_" + index + "_" + duplicate++ + extension;
        return path;
    }

    private String workflowFilename(String requestedName) {
        String candidate = limitText(requestedName.replaceAll("[\\\\/:*?\"<>|]", "_"), 120);
        if (candidate.isBlank()) candidate = "ai-canvas-workflow";
        return candidate.toLowerCase(Locale.ROOT).endsWith(".zip") ? candidate : candidate + ".zip";
    }

    private Map<String, Object> uploadWorkflowBytes(byte[] bytes, String originalName, String contentType) {
        if (bytes.length == 0) throw new IllegalArgumentException("工作流资源为空");
        if (bytes.length > KIE_MEDIA_UPLOAD_MAX_BYTES) throw new IllegalArgumentException("单个工作流资源不能超过 100MB");
        try {
            String name = firstNonBlank(originalName, "workflow-resource.bin");
            String type = firstNonBlank(contentType, "application/octet-stream");
            String objectName = "AI_CANVAS/workflow/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + fileExtension(name, type);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            metadata.setContentType(type);
            ossService.getOssClient().putObject(appProperties.getOss().getInputBucket(), objectName,
                    new ByteArrayInputStream(bytes), metadata);
            String url = appProperties.getOss().getInputPublicHost() + "/" + objectName;
            return Map.of("url", url, "name", name, "kind", mediaKind(name, url));
        } catch (Exception e) {
            throw new RuntimeException("保存工作流资源失败: " + e.getMessage(), e);
        }
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
                "image_models", PROJECT_IMAGE_MODELS,
                "chat_models", textModels(),
                "video_models", PROJECT_VIDEO_MODELS
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
        body.put("media_type", mediaType);
        String servingUrl = canvasTaskService.resultServingUrl(result);
        boolean persistedResultReady = servingUrl != null && !servingUrl.isBlank();
        boolean waitingForLocalCache = "succeeded".equals(status)
                && result.getResultUrl() != null
                && !result.getResultUrl().isBlank()
                && !persistedResultReady;
        // Never expose KIE's temporary result URL. Dev serves /ai-result;
        // prod serves the permanent OSS URL after promotion succeeds.
        if (waitingForLocalCache) status = "running";
        body.put("status", status);
        body.put("cost", result.getCost());
        body.putAll(canvasTaskService.billingFields(taskId));
        body.put("completion_mode", waitingForLocalCache ? canvasTaskService.resultStorageMode() : (useCallbackTaskCompletion() ? "callback" : "polling"));
        body.put("result_storage_pending", waitingForLocalCache);
        body.put("error", waitingForLocalCache ? "正在保存生成结果..." : firstNonBlank(result.getErrorMessage(), ""));
        if ("succeeded".equals(status) && result.getResultUrl() != null && !result.getResultUrl().isBlank()) {
            // 优先使用本地落盘结果（仅本地，不上 OSS），避免 KIE 远程链接过期导致裂图
            body.put("result", Map.of(mediaType.equals("video") ? "videos" : "images", List.of(servingUrl), "url", servingUrl));
        } else {
            body.put("result", Map.of(mediaType.equals("video") ? "videos" : "images", List.of()));
        }
        return body;
    }

    /**
     * 把本地落盘的绝对路径转成前端可访问的服务 URL（/ai-result/** 由 WebMvcConfig 静态映射）。
     * 路径不在 localSaveRoot 之下时返回 null，调用方应回退到 KIE 远程链接。
     */
    private String localServingUrl(String absolutePath) {
        return ossService.localServingUrl(absolutePath);
    }

    private KieTaskResult readTaskResult(String taskId) {
        Optional<KieTaskResult> stored = canvasTaskService.findResult(taskId);
        // A callback can arrive before KIE's billing field. Keep polling a
        // completed task without actual cost so the canvas ledger can replace
        // the estimate with the provider-confirmed amount.
        if (stored.isPresent() && stored.get().isFinished() && stored.get().getCost() != null) {
            return canvasTaskService.ensureResultPersisted(taskId).orElse(stored.get());
        }
        if (stored.isPresent() && useCallbackTaskCompletion() && !stored.get().isFinished()) {
            return stored.get();
        }
        KieTaskResult result = kieClientService.getFullResult(taskId);
        canvasTaskService.recordPolledResult(result);
        return canvasTaskService.findResult(taskId).orElse(result);
    }

    private boolean useCallbackTaskCompletion() {
        return appProperties.getKie() != null
                && appProperties.getKie().getCallbackUrl() != null
                && !appProperties.getKie().getCallbackUrl().isBlank();
    }

    private record WorkflowArchive(byte[] bytes, int sourceCount, int packedCount, int skippedCount) {}

    private record WorkflowImport(Map<String, Object> workflow, int sourceCount, int restoredCount, int skippedCount) {}

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
        if ("CANCELED".equalsIgnoreCase(result.getStatus())) return "cancelled";
        if (result.isSuccess() || "SUCCESS".equalsIgnoreCase(result.getStatus())) return "succeeded";
        if (result.isFinished() || "FAILED".equalsIgnoreCase(result.getStatus())) return "failed";
        return "running";
    }

    private Map<String, Object> uploadFile(MultipartFile file) {
        validateKieUploadFile(file);
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
            return Map.of(
                    "url", url,
                    "name", name,
                    "kind", mediaKind(name, url),
                    "comfy_name", name,
                    "checksum", sha256(bytes),
                    "size", bytes.length
            );
        } catch (Exception e) {
            throw new RuntimeException("上传文件失败: " + e.getMessage(), e);
        }
    }

    private void validateKieUploadFile(MultipartFile file) {
        String name = firstNonBlank(file.getOriginalFilename(), "canvas-upload.bin");
        String kind = mediaKind(name, "");
        long maxBytes = "image".equals(kind) ? KIE_IMAGE_UPLOAD_MAX_BYTES : KIE_MEDIA_UPLOAD_MAX_BYTES;
        if (file.getSize() <= maxBytes) {
            return;
        }
        String label = "image".equals(kind) ? "图片" : "video".equals(kind) ? "视频" : "音频";
        long maxMegabytes = maxBytes / 1024 / 1024;
        throw new IllegalArgumentException(label + "超过 KIE.ai 支持的上传大小上限（" + maxMegabytes + "MB）");
    }

    private void proxyTrustedMedia(String sourceUrl,
                                   String requestedName,
                                   boolean inline,
                                   HttpServletResponse servletResponse) throws IOException {
        URI target;
        try {
            target = trustedMediaUri(sourceUrl);
        } catch (IllegalArgumentException e) {
            servletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        for (int redirectCount = 0; redirectCount <= MEDIA_PROXY_MAX_REDIRECTS; redirectCount += 1) {
            Request request = new Request.Builder()
                    .url(target.toString())
                    .header("User-Agent", "Mozilla/5.0 (compatible; AgentAPlusCanvas/1.0)")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,video/*,audio/*;q=0.8,*/*;q=0.5")
                    .header("Referer", "https://kie.ai/")
                    .build();

            try (Response remoteResponse = mediaProxyClient.newCall(request).execute()) {
                if (isRedirect(remoteResponse.code())) {
                    String location = remoteResponse.header("Location");
                    if (location == null || location.isBlank()) {
                        servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "媒体服务返回了无效的重定向地址");
                        return;
                    }
                    try {
                        target = trustedMediaUri(target.resolve(location).toString());
                    } catch (IllegalArgumentException e) {
                        servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "媒体服务重定向到了不受信任的地址");
                        return;
                    }
                    continue;
                }

                ResponseBody body = remoteResponse.body();
                if (!remoteResponse.isSuccessful() || body == null) {
                    log.warn("Canvas media proxy failed with status {} from host {}", remoteResponse.code(), target.getHost());
                    servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "媒体文件暂时无法读取");
                    return;
                }

                long length = body.contentLength();
                if (length > MEDIA_PROXY_MAX_BYTES) {
                    servletResponse.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "媒体文件超过画布预览上限");
                    return;
                }

                String filename = safeMediaFilename(requestedName, target);
                servletResponse.setStatus(HttpServletResponse.SC_OK);
                servletResponse.setContentType(mediaContentType(remoteResponse.header("Content-Type"), filename));
                servletResponse.setHeader("Cache-Control", "private, max-age=300");
                servletResponse.setHeader("Content-Disposition", contentDisposition(filename, inline));
                if (length >= 0) {
                    servletResponse.setContentLengthLong(length);
                }

                try (InputStream input = body.byteStream(); OutputStream output = servletResponse.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    long copied = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        copied += read;
                        if (copied > MEDIA_PROXY_MAX_BYTES) {
                            log.warn("Canvas media proxy stopped oversized stream from host {}", target.getHost());
                            return;
                        }
                        output.write(buffer, 0, read);
                    }
                }
                return;
            } catch (IOException e) {
                log.warn("Canvas media proxy request failed for host {}", target.getHost(), e);
                if (!servletResponse.isCommitted()) {
                    servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "媒体文件暂时无法读取");
                }
                return;
            }
        }

        servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "媒体服务重定向次数过多");
    }

    private URI trustedMediaUri(String rawUrl) {
        try {
            URI uri = URI.create(textValue(rawUrl).trim());
            String scheme = textValue(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = textValue(uri.getHost()).toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("仅支持 HTTP 或 HTTPS 媒体地址");
            }
            if (host.isBlank() || !isTrustedMediaHost(host)) {
                throw new IllegalArgumentException("媒体地址不在画布可信来源内");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("媒体地址格式无效");
        }
    }

    private boolean isTrustedMediaHost(String host) {
        if (trustedConfiguredMediaHosts().contains(host)) {
            return true;
        }
        if (LIBLIB_MEDIA_HOSTS.contains(host)) {
            return true;
        }
        return KIE_MEDIA_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    private Set<String> trustedConfiguredMediaHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        addConfiguredMediaHost(hosts, appProperties.getOss().getInputPublicHost());
        addConfiguredMediaHost(hosts, appProperties.getOss().getResultPublicHost());
        addConfiguredMediaHost(hosts, appProperties.getKie().getBaseUrl());
        return hosts;
    }

    private void addConfiguredMediaHost(Set<String> hosts, String configuredUrl) {
        String value = textValue(configuredUrl).trim();
        if (value.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                hosts.add(uri.getHost().toLowerCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
            log.warn("Skipping invalid configured media host");
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private String mediaContentType(String responseContentType, String filename) {
        String value = firstNonBlank(responseContentType, "").toLowerCase(Locale.ROOT);
        if (value.startsWith("image/") || value.startsWith("video/") || value.startsWith("audio/")) {
            return responseContentType;
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".webp")) return "image/webp";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".svg")) return "image/svg+xml";
        if (lowerName.endsWith(".mp4")) return "video/mp4";
        if (lowerName.endsWith(".webm")) return "video/webm";
        if (lowerName.endsWith(".mp3")) return "audio/mpeg";
        if (lowerName.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private String safeMediaFilename(String requestedName, URI source) {
        String fallback = "canvas-media" + fileExtension(source.getPath(), "");
        String candidate = firstNonBlank(requestedName, fallback);
        String cleaned = candidate.replaceAll("[\\r\\n\\\\\";]", "_").trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String contentDisposition(String filename, boolean inline) {
        String asciiFilename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (asciiFilename.isBlank()) {
            asciiFilename = "canvas-media";
        }
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return (inline ? "inline" : "attachment") + "; filename=\"" + asciiFilename
                + "\"; filename*=UTF-8''" + encodedFilename;
    }

    private String normalizeInputUrl(String value) {
        String raw = textValue(value).trim();
        if (raw.isBlank()) return raw;
        if (raw.startsWith("/ai-result/")) {
            return uploadLocalCanvasMedia(raw);
        }
        if (!raw.startsWith("data:")) return raw;
        try {
            int comma = raw.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("参考图片 data URL 格式无效");
            String header = raw.substring(0, comma);
            String contentType = header.contains(";") ? header.substring(5, header.indexOf(';')) : "image/png";
            validateVideoReferenceMediaType(contentType);
            String base64 = raw.substring(comma + 1);
            if (base64.contains("%")) {
                base64 = URLDecoder.decode(base64, StandardCharsets.UTF_8);
            }
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            validateVideoReferenceSize(bytes.length, contentType);
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
            log.warn("[Infinite Canvas] data URL 上传 OSS 失败: {}", e.getMessage());
            throw new IllegalArgumentException("视频参考图片无法上传为 KIE 可访问地址：" + e.getMessage());
        }
    }

    /**
     * KIE cannot fetch browser-facing /ai-result/** paths. Convert generated
     * local media to OSS public URLs before they enter a video request.
     */
    private String uploadLocalCanvasMedia(String localUrl) {
        Path localRoot = localSaveRootPath();
        String relative = localUrl.substring("/ai-result/".length()).split("[?#]", 2)[0];
        Path source = localRoot.resolve(relative.replace('/', File.separatorChar)).normalize();
        if (!source.startsWith(localRoot) || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("视频参考图片不存在或不在本地结果目录中：" + localUrl);
        }
        try {
            long size = Files.size(source);
            if (size <= 0L) throw new IllegalArgumentException("视频参考图片为空");
            String contentType = firstNonBlank(Files.probeContentType(source), URLConnection.guessContentTypeFromName(source.getFileName().toString()));
            validateVideoReferenceMediaType(contentType);
            validateVideoReferenceSize(size, contentType);
            String publicHost = textValue(appProperties.getOss().getInputPublicHost()).replaceAll("/+$", "");
            String inputBucket = textValue(appProperties.getOss().getInputBucket());
            if (publicHost.isBlank() || inputBucket.isBlank()) {
                throw new IllegalArgumentException("未配置 OSS 输入桶公网地址，无法将本地结果图提供给 KIE");
            }
            String extension = fileExtension(source.getFileName().toString(), contentType);
            String objectName = "AI_CANVAS/video-reference/" + System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size);
            metadata.setContentType(contentType);
            try (InputStream in = Files.newInputStream(source)) {
                ossService.getOssClient().putObject(inputBucket, objectName, in, metadata);
            }
            String publicUrl = publicHost + "/" + objectName;
            log.info("[Infinite Canvas] uploaded local video reference for KIE: source={}, object={}", source.getFileName(), objectName);
            return publicUrl;
        } catch (IOException e) {
            throw new IllegalArgumentException("读取本地视频参考图片失败：" + e.getMessage());
        }
    }

    private Path localSaveRootPath() {
        String configured = textValue(appProperties.getLocalSaveRoot());
        if (configured.isBlank()) {
            configured = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "D:/AiResult" : "/tmp/ai-result";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private void validateVideoReferenceMediaType(String contentType) {
        String normalized = textValue(contentType).toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("image/") && !normalized.startsWith("video/") && !normalized.startsWith("audio/")) {
            throw new IllegalArgumentException("视频参考素材仅支持图片、视频或音频文件");
        }
    }

    private void validateVideoReferenceSize(long size, String contentType) {
        String normalized = textValue(contentType).toLowerCase(Locale.ROOT);
        long max = normalized.startsWith("image/") ? KIE_VIDEO_REFERENCE_IMAGE_MAX_BYTES
                : normalized.startsWith("audio/") ? KIE_VIDEO_REFERENCE_AUDIO_MAX_BYTES
                : KIE_MEDIA_UPLOAD_MAX_BYTES;
        if (size <= max) return;
        String label = normalized.startsWith("image/") ? "图片" : normalized.startsWith("audio/") ? "音频" : "视频";
        throw new IllegalArgumentException("视频参考" + label + "不能超过 " + (max / 1024 / 1024) + "MB");
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

    private Map<String, Object> videoInput(Map<String, Object> payload, String prompt, String model) {
        if (isMiniMaxH3VideoModel(model)) return miniMaxH3VideoInput(payload, prompt, model);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        input.put("duration", seedanceDuration(payload.get("duration"), model));
        input.put("aspect_ratio", seedanceAspectRatio(textValue(payload.get("aspect_ratio"))));
        input.put("resolution", seedanceResolution(textValue(payload.get("resolution"))));
        input.put("generate_audio", boolValue(payload.get("generate_audio")));
        input.put("return_last_frame", false);
        input.put("web_search", false);
        if (SEEDANCE_2_5_MODEL.equals(model)) {
            input.put("output_format", "mp4");
            input.put("nsfw_checker", true);
        }

        List<VideoImageReference> images = videoImageReferences(payload.get("images"));
        List<String> videos = mediaUrls(payload.get("videos")).stream().map(this::normalizeInputUrl).toList();
        List<String> audios = mediaUrls(payload.get("audios")).stream().map(this::normalizeInputUrl).toList();
        VideoImageReference firstFrame = images.stream().filter(item -> "first_frame".equals(item.role())).findFirst().orElse(null);
        VideoImageReference lastFrame = images.stream().filter(item -> "last_frame".equals(item.role())).findFirst().orElse(null);
        boolean useMultimodal = boolValue(payload.get("multimodal")) || !videos.isEmpty() || !audios.isEmpty();

        if (useMultimodal || (images.size() > 1 && (firstFrame == null || lastFrame == null))) {
            List<String> referenceImages = images.stream().map(VideoImageReference::url).toList();
            if (!referenceImages.isEmpty()) input.put("reference_image_urls", referenceImages);
            if (!videos.isEmpty()) input.put("reference_video_urls", videos);
            if (!audios.isEmpty()) input.put("reference_audio_urls", audios);
        } else if (firstFrame != null && lastFrame != null) {
            input.put("first_frame_url", firstFrame.url());
            input.put("last_frame_url", lastFrame.url());
        } else if (!images.isEmpty()) {
            input.put("first_frame_url", images.get(0).url());
        }
        return input;
    }

    private Map<String, Object> miniMaxH3VideoInput(Map<String, Object> payload, String prompt, String model) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        input.put("duration", miniMaxH3Duration(payload.get("duration")));

        List<VideoImageReference> images = videoImageReferences(payload.get("images"));
        List<String> videos = mediaUrls(payload.get("videos")).stream().map(this::normalizeInputUrl).toList();
        List<String> audios = mediaUrls(payload.get("audios")).stream().map(this::normalizeInputUrl).toList();
        VideoImageReference firstFrame = images.stream().filter(item -> "first_frame".equals(item.role())).findFirst().orElse(null);
        VideoImageReference lastFrame = images.stream().filter(item -> "last_frame".equals(item.role())).findFirst().orElse(null);

        if (MINIMAX_H3_TEXT_MODEL.equals(model)) {
            input.put("aspect_ratio", miniMaxH3AspectRatio(textValue(payload.get("aspect_ratio")), false));
            return input;
        }

        if (MINIMAX_H3_IMAGE_MODEL.equals(model)) {
            VideoImageReference resolvedFirst = firstFrame != null ? firstFrame : images.stream().findFirst().orElse(null);
            if (resolvedFirst == null) throw new IllegalArgumentException("MiniMax H3 图生视频需要至少一张参考图片");
            input.put("first_frame_url", resolvedFirst.url());
            VideoImageReference resolvedLast = lastFrame != null
                    ? lastFrame
                    : images.stream().filter(item -> !item.url().equals(resolvedFirst.url())).findFirst().orElse(null);
            if (resolvedLast != null) input.put("last_frame_url", resolvedLast.url());
            return input;
        }

        if (images.isEmpty() && videos.isEmpty() && audios.isEmpty()) {
            throw new IllegalArgumentException("MiniMax H3 多模态参考需要至少一项图片、视频或音频素材");
        }
        input.put("aspect_ratio", miniMaxH3AspectRatio(textValue(payload.get("aspect_ratio")), true));
        if (!images.isEmpty()) input.put("reference_image_urls", images.stream().map(VideoImageReference::url).toList());
        if (!videos.isEmpty()) input.put("reference_video_urls", videos);
        if (!audios.isEmpty()) input.put("reference_audio_urls", audios);
        return input;
    }

    private boolean isMiniMaxH3VideoModel(String model) {
        return MINIMAX_H3_TEXT_MODEL.equals(model)
                || MINIMAX_H3_IMAGE_MODEL.equals(model)
                || MINIMAX_H3_REFERENCE_MODEL.equals(model);
    }

    private List<VideoImageReference> videoImageReferences(Object value) {
        List<VideoImageReference> references = new ArrayList<>();
        collectVideoImageReferences(value, references);
        return references.stream()
                .filter(item -> !item.url().isBlank())
                .collect(Collectors.toMap(VideoImageReference::url, item -> item, (first, ignored) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    private void collectVideoImageReferences(Object value, List<VideoImageReference> references) {
        if (value == null) return;
        if (value instanceof String text) {
            references.add(new VideoImageReference(normalizeInputUrl(text), ""));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectVideoImageReferences(item, references));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            String url = "";
            for (String key : List.of("url", "image_url", "imageUrl", "src")) {
                if (map.containsKey(key)) {
                    url = textValue(map.get(key));
                    break;
                }
            }
            if (!url.isBlank()) {
                references.add(new VideoImageReference(normalizeInputUrl(url), textValue(map.get("role"))));
            }
        }
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
        Set<String> allowed = referenceMode
                ? Set.of("16:9", "9:16", "1:1", "adaptive")
                : Set.of("16:9", "9:16", "1:1");
        return allowed.contains(value) ? value : "16:9";
    }

    private String seedanceResolution(String value) {
        if ("1080p".equalsIgnoreCase(value)) return "1080p";
        return "480p".equalsIgnoreCase(value) ? "480p" : "720p";
    }

    private String seedanceAspectRatio(String value) {
        return Set.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(value)
                ? value
                : "16:9";
    }

    private String explicitImageResolution(String value) {
        if (value == null || value.isBlank()) return "";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "1K", "2K", "4K" -> value.trim().toUpperCase(Locale.ROOT);
            default -> "";
        };
    }

    private String explicitImageAspectRatio(String value) {
        if (value == null || value.isBlank()) return "";
        String ratio = value.trim();
        return KIE_IMAGE_ASPECT_RATIOS.contains(ratio) ? ratio : "";
    }

    private String normalizeImageModel(String model) {
        // 信任前端传入的模型：前端模型列表来自 /api/config（即 PROJECT_IMAGE_MODELS），
        // 仅当为空 / 非法前缀时回退默认模型，避免「选了 GPT 实际跑 nano」的静默降级。
        if (model == null || model.isBlank() || model.startsWith("project-")) return PROJECT_IMAGE_MODEL;
        return model;
    }

    private String normalizeVideoModel(String model) {
        if (model == null || model.isBlank() || model.startsWith("project-")) return PROJECT_VIDEO_MODEL;
        return PROJECT_VIDEO_MODELS.contains(model.trim()) ? model.trim() : PROJECT_VIDEO_MODEL;
    }

    private record VideoImageReference(String url, String role) {
    }

    private String normalizeCanvasKind(String value) {
        return "smart".equalsIgnoreCase(value) ? "smart" : "classic";
    }

    private void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.containsKey(key)) to.put(key, from.get(key));
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) return toStringObjectMap(map);
        return new LinkedHashMap<>();
    }

    private List<Object> objectList(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        return List.of();
    }

    private List<Map<String, Object>> mapList(Map<String, Object> container, String key) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object value : objectList(container.get(key))) {
            if (value instanceof Map<?, ?> map) normalized.add(toStringObjectMap(map));
        }
        container.put(key, normalized);
        return normalized;
    }

    private String limitText(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder text = new StringBuilder(digest.length * 2);
            for (byte value : digest) text.append(String.format("%02x", value));
            return text.toString();
        } catch (Exception error) {
            log.warn("Unable to calculate canvas asset checksum: {}", error.getMessage());
            return "";
        }
    }

    private String nameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && !path.isBlank()) {
                int slash = path.lastIndexOf('/');
                String name = path.substring(slash + 1);
                if (!name.isBlank()) return URLDecoder.decode(name, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Use the generic name below for malformed or data URLs.
        }
        return "素材";
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

    private void requirePriceAdmin(HttpServletRequest request) {
        if (!isPriceAdmin(request)) {
            throw new IllegalArgumentException("无权限：仅超级管理员可以维护模型价格目录");
        }
    }

    private boolean isPriceAdmin(HttpServletRequest request) {
        // `operator` is the user's real name; system-management access is
        // consistently scoped to the PINKSIR shop everywhere in the UI.
        return "PINKSIR".equalsIgnoreCase(currentShopName(request).trim());
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
        if (text.endsWith(".json") || text.endsWith(".zip")) return "workflow";
        if (text.endsWith(".txt") || text.endsWith(".csv") || text.endsWith(".md") || text.endsWith(".srt") || text.endsWith(".vtt")) return "file";
        return "image";
    }
}
