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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasTaskService {

    public static final int MAX_ACTIVE_TASKS = 10;

    private final CanvasTaskRepository canvasTaskRepository;
    private final ObjectMapper objectMapper;
    private final OssService ossService;
    private final AppProperties appProperties;

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
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (CanvasTask task : canvasTaskRepository.findTop100ByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator)) {
            String status = normalizeStatus(task.getStatus(), task.getResultUrl());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("task_id", task.getTaskId());
            item.put("status", status.toLowerCase());
            item.put("media_type", task.getMediaType());
            String resultUrl = task.getResultUrl() == null ? "" : task.getResultUrl();
            String localUrl = localServingUrl(task.getLocalPath());
            if (localUrl != null && task.getLocalPath() != null && new File(task.getLocalPath()).exists()) {
                resultUrl = localUrl;
            }
            item.put("result_url", resultUrl);
            item.put("error", task.getErrorMessage() == null ? "" : task.getErrorMessage());
            item.put("terminal", isTerminalStatus(status));
            item.put("retryable", "FAILED".equals(status) && task.getRequestPayloadJson() != null && !task.getRequestPayloadJson().isBlank());
            item.put("created_at", task.getCreatedAt() == null ? 0L : task.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            item.put("updated_at", task.getUpdatedAt() == null ? 0L : task.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            tasks.add(item);
        }
        return tasks;
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
            return;
        }
        task.setStatus(normalizeStatus(result.getStatus(), result.getResultUrl()));
        task.setResultUrl(blankToNull(result.getResultUrl()));
        task.setErrorMessage(blankToNull(result.getErrorMessage()));
        if (callbackPayloadJson != null) {
            task.setCallbackPayloadJson(callbackPayloadJson);
        }
        // 🔴 AI 画布结果本地落盘（仅本地，不上 OSS）：成功后把 KIE 远程图下载到 D:/AiResult
        if (task.getLocalPath() == null
                && ("SUCCESS".equalsIgnoreCase(result.getStatus()) || result.isSuccess())
                && result.getResultUrl() != null && !result.getResultUrl().isBlank()) {
            try {
                String localPath = ossService.downloadResultToLocal(task.getTaskId(), result.getResultUrl());
                if (localPath != null) task.setLocalPath(localPath);
            } catch (Exception dlEx) {
                log.warn("[AI Canvas] 结果本地落盘失败，继续保留 KIE 远程链接: taskId={}, error={}", task.getTaskId(), dlEx.getMessage());
            }
        }
        canvasTaskRepository.save(task);
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
                .errorMessage(task.getErrorMessage())
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
        return KieTaskResult.builder()
                .taskId(taskId)
                .status(status)
                .finished("SUCCESS".equals(status) || "FAILED".equals(status))
                .success("SUCCESS".equals(status))
                .resultUrl(resultUrl)
                .errorMessage(errorMessage)
                .build();
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
