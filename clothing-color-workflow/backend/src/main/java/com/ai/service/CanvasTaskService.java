package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.entity.CanvasTask;
import com.ai.repository.CanvasTaskRepository;
import com.ai.service.OssService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasTaskService {

    public static final int MAX_ACTIVE_TASKS = 10;

    private final CanvasTaskRepository canvasTaskRepository;
    private final ObjectMapper objectMapper;
    private final OssService ossService;
    private final AppProperties appProperties;
    private final Environment environment;
    private final ModelPricingService modelPricingService;

    @Transactional
    public void recordCreated(String taskId, String mediaType, String operator, String shopName) {
        recordCreated(taskId, mediaType, operator, shopName, null);
    }

    @Transactional
    public void recordCreated(String taskId,
                              String mediaType,
                              String operator,
                              String shopName,
                              Map<String, Object> requestPayload) {
        if (taskId == null || taskId.isBlank()) return;
        CanvasTask task = canvasTaskRepository.findByTaskId(taskId).orElseGet(CanvasTask::new);
        task.setTaskId(taskId);
        task.setMediaType(mediaType == null || mediaType.isBlank() ? "image" : mediaType);
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("PROCESSING");
        }
        task.setOperator(operator);
        task.setShopName(shopName);
        if (requestPayload != null) {
            String canvasId = requestPayload.get("canvas_id") == null ? "" : String.valueOf(requestPayload.get("canvas_id")).trim();
            task.setCanvasId(blankToNull(canvasId));
            String canvasNodeId = requestPayload.get("canvas_node_id") == null ? "" : String.valueOf(requestPayload.get("canvas_node_id")).trim();
            task.setCanvasNodeId(blankToNull(canvasNodeId));
            ModelPricingService.PriceQuote quote = modelPricingService.quote(task.getMediaType(), requestPayload, 1);
            task.setEstimatedCost(quote.amountCny());
            task.setPriceVersion(blankToNull(quote.versionCode()));
            task.setPriceSnapshotJson(modelPricingService.quoteSnapshotJson(quote));
        }
        if (requestPayload != null && !requestPayload.isEmpty()) {
            try {
                task.setRequestPayloadJson(objectMapper.writeValueAsString(requestPayload));
            } catch (Exception e) {
                log.warn("[AI Canvas] task request snapshot was not saved: taskId={}, error={}", taskId, e.getMessage());
            }
        }
        canvasTaskRepository.save(task);
    }

    /**
     * The retry request is scoped to the account that originally created it.
     * Older tasks without a snapshot remain queryable, but are not retried.
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> retryPayload(String taskId, String operator, String shopName) {
        if (taskId == null || taskId.isBlank()) return Optional.empty();
        return canvasTaskRepository.findByTaskId(taskId).flatMap(task -> {
            if (!sameOwner(task, operator, shopName) || task.getRequestPayloadJson() == null || task.getRequestPayloadJson().isBlank()) {
                return Optional.empty();
            }
            try {
                Map<String, Object> payload = objectMapper.readValue(task.getRequestPayloadJson(), new TypeReference<>() {});
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("media_type", task.getMediaType());
                result.put("payload", payload);
                return Optional.of(result);
            } catch (Exception e) {
                log.warn("[AI Canvas] task request snapshot was not readable: taskId={}, error={}", taskId, e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<KieTaskResult> findResult(String taskId) {
        return canvasTaskRepository.findByTaskId(taskId).map(this::toResult);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> billingFields(String taskId) {
        return canvasTaskRepository.findByTaskId(taskId).map(task -> {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("estimated_cost", task.getEstimatedCost());
            fields.put("actual_cost", task.getActualCost());
            fields.put("price_version", task.getPriceVersion() == null ? "" : task.getPriceVersion());
            fields.put("price_snapshot", readJson(task.getPriceSnapshotJson()));
            return fields;
        }).orElse(Map.of());
    }

    /**
     * Dev serves a local LAN copy; prod serves only a result that has been
     * promoted from KIE's temporary URL to the permanent OSS bucket.
     */
    @Transactional
    public Optional<KieTaskResult> ensureResultPersisted(String taskId) {
        if (taskId == null || taskId.isBlank()) return Optional.empty();
        return canvasTaskRepository.findByTaskId(taskId).map(task -> {
            if (isSuccessfulResult(task) && !hasPersistedResult(task)) {
                persistResult(task);
                canvasTaskRepository.save(task);
            }
            return toResult(task);
        });
    }

    /**
     * Retry the profile's selected storage target even after the browser closes.
     */
    @Scheduled(fixedDelayString = "${app.canvas.local-cache-retry-ms:60000}")
    @Transactional
    public void retryPendingLocalResultCache() {
        for (CanvasTask task : canvasTaskRepository.findTop20ByStatusIgnoreCaseOrderByUpdatedAtAsc("SUCCESS")) {
            if (!hasPersistedResult(task)) {
                persistResult(task);
                canvasTaskRepository.save(task);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> taskCapacity(String operator, String shopName) {
        int active = Math.toIntExact(canvasTaskRepository.countByShopNameAndOperatorAndStatusIgnoreCase(shopName, operator, "PROCESSING"));
        return Map.of(
                "active", active,
                "max", MAX_ACTIVE_TASKS,
                "available", Math.max(0, MAX_ACTIVE_TASKS - active)
        );
    }

    /**
     * The browser also has a queue, but this server-side guard prevents a
     * second tab or a refresh from exceeding the account-level task limit.
     */
    @Transactional(readOnly = true)
    public void requireSubmissionCapacity(String operator, String shopName) {
        Map<String, Integer> capacity = taskCapacity(operator, shopName);
        if (capacity.get("active") >= capacity.get("max")) {
            throw new IllegalStateException("画布同时最多可运行 " + MAX_ACTIVE_TASKS + " 个任务，请等待完成或停止等待后再提交。");
        }
    }

    /**
     * The canvas keeps its own task ledger so a page refresh does not lose the
     * user's queue, timing information, or a completed result.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentTasks(String operator, String shopName) {
        return recentTasks(operator, shopName, "");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentTasks(String operator, String shopName, String canvasId) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<CanvasTask> rows = canvasId == null || canvasId.isBlank()
                ? canvasTaskRepository.findTop100ByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator)
                : canvasTaskRepository.findTop100ByShopNameAndOperatorAndCanvasIdOrderByUpdatedAtDesc(shopName, operator, canvasId);
        for (CanvasTask task : rows) {
            String status = normalizeStatus(task.getStatus(), task.getResultUrl());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("task_id", task.getTaskId());
            item.put("status", status.toLowerCase());
            item.put("media_type", task.getMediaType());
            String resultUrl = resultServingUrl(task);
            item.put("result_url", resultUrl);
            item.put("result_urls", resultServingUrls(task));
            item.put("error", task.getErrorMessage() == null ? "" : task.getErrorMessage());
            item.put("terminal", isTerminalStatus(status));
            item.put("retryable", "FAILED".equals(status) && task.getRequestPayloadJson() != null && !task.getRequestPayloadJson().isBlank());
            item.put("canvas_id", task.getCanvasId() == null ? "" : task.getCanvasId());
            item.put("canvas_node_id", task.getCanvasNodeId() == null ? "" : task.getCanvasNodeId());
            item.put("estimated_cost", task.getEstimatedCost());
            item.put("actual_cost", task.getActualCost());
            item.put("price_version", task.getPriceVersion() == null ? "" : task.getPriceVersion());
            item.put("price_snapshot", readJson(task.getPriceSnapshotJson()));
            item.put("created_at", task.getCreatedAt() == null ? 0L : task.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            item.put("updated_at", task.getUpdatedAt() == null ? 0L : task.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            tasks.add(item);
        }
        return tasks;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> billingSummary(String operator, String shopName, String canvasId) {
        List<Map<String, Object>> tasks = recentTasks(operator, shopName, canvasId);
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal pendingEstimate = BigDecimal.ZERO;
        for (Map<String, Object> task : tasks) {
            BigDecimal actualCost = decimal(task.get("actual_cost"));
            BigDecimal estimatedCost = decimal(task.get("estimated_cost"));
            if (actualCost != null) {
                actual = actual.add(actualCost);
            } else if ("running".equalsIgnoreCase(String.valueOf(task.get("status")))
                    || "queued".equalsIgnoreCase(String.valueOf(task.get("status")))) {
                pendingEstimate = pendingEstimate.add(estimatedCost == null ? BigDecimal.ZERO : estimatedCost);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canvas_id", canvasId == null ? "" : canvasId);
        result.put("actual_cost", actual.setScale(4, java.math.RoundingMode.HALF_UP));
        result.put("pending_estimate", pendingEstimate.setScale(4, java.math.RoundingMode.HALF_UP));
        result.put("tasks", tasks);
        return result;
    }

    /**
     * KIE image tasks do not expose a reliable cross-model cancellation endpoint.
     * Keep the user's canvas from waiting for the task while preserving the remote task as-is.
     */
    @Transactional
    public boolean cancelTracking(String taskId) {
        if (taskId == null || taskId.isBlank()) return false;
        return canvasTaskRepository.findByTaskId(taskId).map(task -> {
            String status = normalizeStatus(task.getStatus(), task.getResultUrl());
            if (isTerminalStatus(status)) return false;
            task.setStatus("CANCELED");
            task.setErrorMessage("已停止在画布中等待；KIE 服务端任务可能仍会继续完成。");
            canvasTaskRepository.save(task);
            return true;
        }).orElse(false);
    }

    @Transactional
    public void recordPolledResult(KieTaskResult result) {
        if (result == null || result.getTaskId() == null || result.getTaskId().isBlank()) return;
        canvasTaskRepository.findByTaskId(result.getTaskId()).ifPresent(task -> applyResult(task, result, null));
    }

    @Transactional
    public boolean refreshTaskByCallback(Map<String, Object> payload) {
        String taskId = extractTaskId(payload);
        if (taskId.isBlank()) {
            log.warn("[AI Canvas Callback] missing taskId: {}", payload);
            return false;
        }
        Optional<CanvasTask> optionalTask = canvasTaskRepository.findByTaskId(taskId);
        if (optionalTask.isEmpty()) {
            return false;
        }
        KieTaskResult result = parseCallback(payload, taskId);
        String payloadJson = "";
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[AI Canvas Callback] serialize payload failed: {}", e.getMessage());
        }
        applyResult(optionalTask.get(), result, payloadJson);
        log.info("[AI Canvas Callback] task refreshed: taskId={}, status={}", taskId, result.getStatus());
        return true;
    }

    private void applyResult(CanvasTask task, KieTaskResult result, String callbackPayloadJson) {
        if ("CANCELED".equalsIgnoreCase(task.getStatus())) {
            // Stopping only removes the task from the canvas waiting queue. KIE
            // may still settle a charge afterwards, which must remain auditable.
            if (result.getCost() != null && result.getCost().signum() != 0) {
                task.setActualCost(modelPricingService.kieCreditsToCny(result.getCost(), task.getPriceVersion()));
            }
            if (callbackPayloadJson != null) task.setCallbackPayloadJson(callbackPayloadJson);
            canvasTaskRepository.save(task);
            return;
        }
        task.setStatus(normalizeStatus(result.getStatus(), result.getResultUrl()));
        task.setResultUrl(blankToNull(result.getResultUrl()));
        List<String> providerUrls = normalizedResultUrls(result.getResultUrls(), result.getResultUrl());
        task.setResultUrlsJson(writeJson(providerUrls));
        task.setErrorMessage(blankToNull(result.getErrorMessage()));
        if (result.getCost() != null && result.getCost().signum() != 0) {
            task.setActualCost(modelPricingService.kieCreditsToCny(result.getCost(), task.getPriceVersion()));
        }
        if (callbackPayloadJson != null) {
            task.setCallbackPayloadJson(callbackPayloadJson);
        }
        // Persist each completed KIE result according to the active profile.
        if (isSuccessfulResult(task)) {
            persistResult(task);
        }
        canvasTaskRepository.save(task);
    }

    private boolean isSuccessfulResult(CanvasTask task) {
        return task != null
                && "SUCCESS".equalsIgnoreCase(task.getStatus())
                && task.getResultUrl() != null
                && !task.getResultUrl().isBlank();
    }

    private boolean isUsableLocalPath(String localPath) {
        if (localPath == null || localPath.isBlank()) return false;
        File localFile = new File(localPath);
        return localFile.isFile() && localFile.length() > 0 && localServingUrl(localPath) != null;
    }

    private List<String> normalizedResultUrls(List<String> urls, String fallback) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (urls != null) urls.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).forEach(values::add);
        if (values.isEmpty() && fallback != null && !fallback.isBlank()) values.add(fallback.trim());
        return new ArrayList<>(values);
    }

    private List<String> readUrlList(String json, String fallback) {
        if (json != null && !json.isBlank()) {
            try {
                List<String> values = objectMapper.readValue(json, new TypeReference<>() {});
                return normalizedResultUrls(values, fallback);
            } catch (Exception ignored) {
                // Older rows do not have the JSON column; retain their primary URL/path.
            }
        }
        return normalizedResultUrls(List.of(), fallback);
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存画布多结果媒体", exception);
        }
    }

    public String resultServingUrl(KieTaskResult result) {
        if (result == null) return null;
        if (usesPermanentOssStorage()) {
            return isPermanentOssUrl(result.getResultUrl()) ? result.getResultUrl() : null;
        }
        if (result.getLocalPath() == null || result.getLocalPath().isBlank()) return null;
        File localFile = new File(result.getLocalPath());
        return localFile.isFile() && localFile.length() > 0 ? localServingUrl(result.getLocalPath()) : null;
    }

    @Transactional(readOnly = true)
    public List<String> resultServingUrls(String taskId) {
        if (taskId == null || taskId.isBlank()) return List.of();
        return canvasTaskRepository.findByTaskId(taskId).map(this::resultServingUrls).orElse(List.of());
    }

    public String resultStorageMode() {
        return usesPermanentOssStorage() ? "permanent-oss" : "local-cache";
    }

    private String resultServingUrl(CanvasTask task) {
        return resultServingUrls(task).stream().findFirst().orElse("");
    }

    private List<String> resultServingUrls(CanvasTask task) {
        if (task == null) return List.of();
        if (usesPermanentOssStorage()) {
            return readUrlList(task.getResultUrlsJson(), task.getResultUrl()).stream()
                    .filter(this::isPermanentOssUrl)
                    .toList();
        }
        return readUrlList(task.getLocalPathsJson(), task.getLocalPath()).stream()
                .map(this::localServingUrl)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean hasPersistedResult(CanvasTask task) {
        if (task == null) return false;
        List<String> expected = readUrlList(task.getResultUrlsJson(), task.getResultUrl());
        if (expected.isEmpty()) return false;
        if (usesPermanentOssStorage()) return expected.stream().allMatch(this::isPermanentOssUrl);
        List<String> localPaths = readUrlList(task.getLocalPathsJson(), task.getLocalPath());
        return localPaths.size() == expected.size() && localPaths.stream().allMatch(this::isUsableLocalPath);
    }

    private boolean usesPermanentOssStorage() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    private boolean isPermanentOssUrl(String value) {
        if (value == null || value.isBlank() || appProperties.getOss() == null) return false;
        String host = appProperties.getOss().getResultPublicHost();
        if (host == null || host.isBlank()) return false;
        String prefix = host.endsWith("/") ? host : host + "/";
        return value.startsWith(prefix);
    }

    private void persistResult(CanvasTask task) {
        if (usesPermanentOssStorage()) {
            cacheResultPermanently(task);
        } else {
            cacheResultLocally(task);
        }
    }

    private void cacheResultPermanently(CanvasTask task) {
        if (!isSuccessfulResult(task) || hasPersistedResult(task)) return;
        try {
            List<String> permanentUrls = new ArrayList<>();
            for (String providerUrl : readUrlList(task.getResultUrlsJson(), task.getResultUrl())) {
                String permanentUrl = isPermanentOssUrl(providerUrl)
                        ? providerUrl
                        : extractOssUrl(ossService.uploadResultToOss("canvas", providerUrl, task.getId(), true));
                if (!isPermanentOssUrl(permanentUrl)) {
                    log.warn("[AI Canvas] permanent OSS result is pending retry: taskId={}", task.getTaskId());
                    return;
                }
                permanentUrls.add(permanentUrl);
            }
            if (!permanentUrls.isEmpty()) {
                task.setResultUrl(permanentUrls.get(0));
                task.setResultUrlsJson(writeJson(permanentUrls));
                task.setLocalPath(null);
                task.setLocalPathsJson(null);
                log.info("[AI Canvas] result promoted to permanent OSS: taskId={}", task.getTaskId());
            }
        } catch (Exception storageEx) {
            log.warn("[AI Canvas] permanent OSS storage failed; it will retry before serving: taskId={}, error={}", task.getTaskId(), storageEx.getMessage());
        }
    }

    private String extractOssUrl(String transferResult) {
        if (transferResult == null || transferResult.isBlank()) return "";
        int separator = transferResult.indexOf('|');
        return (separator >= 0 ? transferResult.substring(0, separator) : transferResult).trim();
    }

    private void cacheResultLocally(CanvasTask task) {
        if (!isSuccessfulResult(task) || hasPersistedResult(task)) return;
        if (task.getLocalPath() != null && !task.getLocalPath().isBlank()) {
            log.warn("[AI Canvas] local result is missing; downloading again: taskId={}, path={}", task.getTaskId(), task.getLocalPath());
            task.setLocalPath(null);
        }
        try {
            // 画布按店铺分区落盘：店铺名为空时回退到“未知店铺”，与前端默认一致
            String shopName = (task.getShopName() == null || task.getShopName().isBlank()) ? "未知店铺" : task.getShopName();
            List<String> localPaths = new ArrayList<>();
            List<String> providerUrls = readUrlList(task.getResultUrlsJson(), task.getResultUrl());
            for (int index = 0; index < providerUrls.size(); index += 1) {
                String localPath = ossService.downloadResultToLocal("canvas", shopName, task.getTaskId() + "_" + (index + 1), providerUrls.get(index));
                if (localPath == null || localPath.isBlank()) {
                    log.warn("[AI Canvas] local result download is pending retry: taskId={}", task.getTaskId());
                    return;
                }
                localPaths.add(localPath);
            }
            if (!localPaths.isEmpty()) {
                task.setLocalPath(localPaths.get(0));
                task.setLocalPathsJson(writeJson(localPaths));
                log.info("[AI Canvas] result cached for LAN access: taskId={}, path={}", task.getTaskId(), localPaths.get(0));
            }
        } catch (Exception dlEx) {
            log.warn("[AI Canvas] local result download failed; it will retry before serving: taskId={}, error={}", task.getTaskId(), dlEx.getMessage());
        }
    }

    private KieTaskResult toResult(CanvasTask task) {
        String status = normalizeStatus(task.getStatus(), task.getResultUrl());
        boolean finished = isTerminalStatus(status);
        return KieTaskResult.builder()
                .taskId(task.getTaskId())
                .status(status)
                .finished(finished)
                .success("SUCCESS".equals(status))
                .resultUrl(task.getResultUrl())
                .resultUrls(readUrlList(task.getResultUrlsJson(), task.getResultUrl()))
                .errorMessage(task.getErrorMessage())
                .cost(task.getActualCost())
                .localPath(task.getLocalPath())
                .build();
    }

    /**
     * 把本地落盘的绝对路径转成前端可访问的服务 URL（/ai-result/** 由 WebMvcConfig 静态映射）。
     * 路径不在 localSaveRoot 之下时返回 null，调用方应回退到 KIE 远程链接。
     */
    private String localServingUrl(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) return null;
        String root = appProperties.getLocalSaveRoot();
        if (root == null) {
            String os = System.getProperty("os.name").toLowerCase();
            root = os.contains("win") ? "D:/AiResult" : "/tmp/ai-result";
        }
        String normAbs = absolutePath.replace('\\', '/');
        String normRoot = root.replace('\\', '/');
        if (normAbs.startsWith(normRoot)) {
            String rel = normAbs.substring(normRoot.length()).replaceAll("^/+", "");
            return "/ai-result/" + rel;
        }
        return null;
    }

    private KieTaskResult parseCallback(Map<String, Object> payload, String taskId) {
        JsonNode root = objectMapper.valueToTree(payload == null ? Map.of() : payload);
        String resultUrl = extractUrl(root);
        LinkedHashSet<String> orderedUrls = new LinkedHashSet<>();
        if (!resultUrl.isBlank()) orderedUrls.add(resultUrl);
        orderedUrls.addAll(extractUrls(root));
        List<String> resultUrls = new ArrayList<>(orderedUrls);
        String rawStatus = firstNonBlank(
                textAt(root, "data", "state"),
                textAt(root, "data", "status"),
                textAt(root, "state"),
                textAt(root, "status"),
                textAt(root, "msg"),
                textAt(root, "message")
        );
        String status = normalizeStatus(rawStatus, resultUrl);
        String errorMessage = "FAILED".equals(status)
                ? firstNonBlank(textAt(root, "data", "failMsg"), textAt(root, "failMsg"), textAt(root, "error"), textAt(root, "message"), textAt(root, "msg"))
                : "";
        BigDecimal cost = extractCost(root);
        return KieTaskResult.builder()
                .taskId(taskId)
                .status(status)
                .finished("SUCCESS".equals(status) || "FAILED".equals(status))
                .success("SUCCESS".equals(status))
                .resultUrl(resultUrl)
                .resultUrls(resultUrls)
                .errorMessage(errorMessage)
                .cost(cost)
                .build();
    }

    private BigDecimal extractCost(JsonNode root) {
        if (root == null || root.isNull()) return null;
        for (String key : List.of("cost", "fee", "amount", "price", "estimatedCost", "totalFee", "costAmount", "charge", "expenses", "totalCost", "costFee", "creditsConsumed", "consumedCredits", "credit", "credits", "consumeCredits", "pointsConsumed", "pointConsumed")) {
            JsonNode node = root.path("data").path(key);
            if (node.isMissingNode() || node.isNull()) node = root.path(key);
            if (node.isMissingNode() || node.isNull()) continue;
            if (node.isNumber()) return node.decimalValue();
            try {
                String value = node.asText().replaceAll("[^0-9.\\-]", "");
                if (!value.isBlank()) return new BigDecimal(value);
            } catch (Exception ignored) {
                // Continue checking the compatibility field names.
            }
        }
        return null;
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractTaskId(Map<String, Object> payload) {
        if (payload == null) return "";
        JsonNode root = objectMapper.valueToTree(payload);
        return firstNonBlank(
                textAt(root, "taskId"),
                textAt(root, "task_id"),
                textAt(root, "data", "taskId"),
                textAt(root, "data", "task_id"),
                textAt(root, "id"),
                textAt(root, "data", "id")
        );
    }

    private String extractUrl(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isObject()) {
            String fromResultJson = extractResultJson(node);
            if (!fromResultJson.isBlank()) return fromResultJson;

            String[] directKeys = {"resultUrl", "imageUrl", "image_url", "videoUrl", "video_url", "url", "output"};
            for (String key : directKeys) {
                String value = textAt(node, key);
                if (looksLikeUrl(value)) return value;
            }
            JsonNode resultUrls = node.get("resultUrls");
            if (resultUrls != null && resultUrls.isArray()) {
                for (JsonNode item : resultUrls) {
                    String value = item.isTextual() ? item.asText() : extractUrl(item);
                    if (looksLikeUrl(value)) return value;
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String value = extractUrl(field.getValue());
                if (looksLikeUrl(value)) return value;
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = extractUrl(item);
                if (looksLikeUrl(value)) return value;
            }
        }
        if (node.isTextual() && looksLikeUrl(node.asText())) {
            return node.asText();
        }
        return "";
    }

    private List<String> extractUrls(JsonNode node) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectResultUrls(node, urls);
        return new ArrayList<>(urls);
    }

    private void collectResultUrls(JsonNode node, LinkedHashSet<String> urls) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            if (looksLikeUrl(node.asText())) urls.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectResultUrls(item, urls));
            return;
        }
        if (!node.isObject()) return;
        JsonNode resultJson = node.get("resultJson");
        if (resultJson != null && !resultJson.isNull()) {
            try {
                collectResultUrls(resultJson.isTextual() ? objectMapper.readTree(resultJson.asText()) : resultJson, urls);
            } catch (Exception ignored) {
                // Keep extracting the regular result fields below.
            }
        }
        for (String key : List.of("resultUrls", "urls", "lastFrameUrl", "last_frame_url", "videoUrl", "video_url", "imageUrl", "image_url", "url", "output")) {
            if (node.has(key)) collectResultUrls(node.get(key), urls);
        }
        node.fields().forEachRemaining(entry -> {
            if (!"resultJson".equals(entry.getKey()) && !Set.of("resultUrls", "urls", "lastFrameUrl", "last_frame_url", "videoUrl", "video_url", "imageUrl", "image_url", "url", "output").contains(entry.getKey())) {
                collectResultUrls(entry.getValue(), urls);
            }
        });
    }

    private String extractResultJson(JsonNode node) {
        JsonNode resultJson = node.get("resultJson");
        if (resultJson == null || resultJson.isNull()) return "";
        try {
            JsonNode parsed = resultJson.isTextual() ? objectMapper.readTree(resultJson.asText()) : resultJson;
            return extractUrl(parsed);
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizeStatus(String rawStatus, String resultUrl) {
        String raw = rawStatus == null ? "" : rawStatus.trim().toLowerCase();
        if (raw.contains("cancel")) {
            return "CANCELED";
        }
        if (raw.contains("success") || raw.contains("succeeded") || raw.contains("completed") || raw.contains("finish")) {
            return "SUCCESS";
        }
        if (raw.contains("fail") || raw.contains("error")) {
            return "FAILED";
        }
        if (looksLikeUrl(resultUrl)) {
            return "SUCCESS";
        }
        return "PROCESSING";
    }

    private boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status);
    }

    private String textAt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull() || !current.has(key)) return "";
            current = current.get(key);
        }
        if (current == null || current.isNull()) return "";
        return current.isValueNode() ? current.asText("") : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private boolean looksLikeUrl(String value) {
        if (value == null) return false;
        String text = value.trim();
        return text.startsWith("http://") || text.startsWith("https://") || text.startsWith("data:");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean sameOwner(CanvasTask task, String operator, String shopName) {
        return Objects.equals(blankToEmpty(task.getOperator()), blankToEmpty(operator))
                && Objects.equals(blankToEmpty(task.getShopName()), blankToEmpty(shopName));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
