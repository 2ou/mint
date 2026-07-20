package com.ai.service;

import com.ai.dto.KieTaskResult;
import com.ai.entity.CanvasTask;
import com.ai.repository.CanvasTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasTaskService {

    private final CanvasTaskRepository canvasTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordCreated(String taskId, String mediaType, String operator, String shopName) {
        if (taskId == null || taskId.isBlank()) return;
        CanvasTask task = canvasTaskRepository.findByTaskId(taskId).orElseGet(CanvasTask::new);
        task.setTaskId(taskId);
        task.setMediaType(mediaType == null || mediaType.isBlank() ? "image" : mediaType);
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("PROCESSING");
        }
        task.setOperator(operator);
        task.setShopName(shopName);
        canvasTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Optional<KieTaskResult> findResult(String taskId) {
        return canvasTaskRepository.findByTaskId(taskId).map(this::toResult);
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
        task.setStatus(normalizeStatus(result.getStatus(), result.getResultUrl()));
        task.setResultUrl(blankToNull(result.getResultUrl()));
        task.setErrorMessage(blankToNull(result.getErrorMessage()));
        if (callbackPayloadJson != null) {
            task.setCallbackPayloadJson(callbackPayloadJson);
        }
        canvasTaskRepository.save(task);
    }

    private KieTaskResult toResult(CanvasTask task) {
        String status = normalizeStatus(task.getStatus(), task.getResultUrl());
        boolean finished = "SUCCESS".equals(status) || "FAILED".equals(status);
        return KieTaskResult.builder()
                .taskId(task.getTaskId())
                .status(status)
                .finished(finished)
                .success("SUCCESS".equals(status))
                .resultUrl(task.getResultUrl())
                .errorMessage(task.getErrorMessage())
                .build();
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
        if (raw.contains("success") || raw.contains("succeeded") || raw.contains("completed") || raw.contains("finish")) {
            return "SUCCESS";
        }
        if (raw.contains("fail") || raw.contains("error") || raw.contains("cancel")) {
            return "FAILED";
        }
        if (looksLikeUrl(resultUrl)) {
            return "SUCCESS";
        }
        return "PROCESSING";
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
}
