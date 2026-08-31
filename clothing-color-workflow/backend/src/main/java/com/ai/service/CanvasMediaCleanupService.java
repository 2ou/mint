package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.entity.CanvasProject;
import com.ai.entity.CanvasTask;
import com.ai.repository.CanvasProjectRepository;
import com.ai.repository.CanvasTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Safely removes a canvas generation log and, on explicit request, resets the
 * result nodes and deletes local generated files that are no longer referenced.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CanvasMediaCleanupService {

    private static final Set<String> TASK_ID_KEYS = Set.of("task_id", "taskid", "prompt_id", "promptid");

    private final CanvasProjectRepository canvasProjectRepository;
    private final CanvasTaskRepository canvasTaskRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public CleanupPlan prepare(Map<String, Object> canvas,
                               String logId,
                               boolean deleteMedia,
                               boolean resetReferencingNodes) {
        if (canvas == null) throw new IllegalArgumentException("画布内容为空");
        String cleanLogId = text(logId);
        if (cleanLogId.isBlank()) throw new IllegalArgumentException("缺少日志 ID");

        List<Map<String, Object>> logs = mapList(canvas.get("logs"));
        Map<String, Object> target = logs.stream()
                .filter(item -> cleanLogId.equals(text(item.get("id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("生成日志不存在"));

        Set<String> taskIds = new LinkedHashSet<>();
        collectTaskIds(target, taskIds);
        Set<Path> candidates = deleteMedia ? generatedPaths(target.get("outputs")) : new LinkedHashSet<>();
        List<String> resetNodeIds = new ArrayList<>();

        if (deleteMedia && resetReferencingNodes && !candidates.isEmpty()) {
            expandOwnedResultPaths(canvas, candidates);
            resetNodeIds.addAll(resetResultNodes(canvas, candidates));
        }

        if (deleteMedia && !candidates.isEmpty()) {
            logs.removeIf(item -> cleanLogId.equals(text(item.get("id")))
                    || referencesAnyPath(item.get("outputs"), candidates));
        } else {
            logs.removeIf(item -> cleanLogId.equals(text(item.get("id"))));
        }
        canvas.put("logs", logs);
        canvas.put("updated_at", System.currentTimeMillis());
        return new CleanupPlan(canvas, candidates, taskIds, resetNodeIds, deleteMedia);
    }

    /**
     * Must run after the updated canvas snapshot has been persisted. This makes
     * the reference scan observe the reset nodes and removed logs.
     */
    @Transactional
    public CleanupResult finish(CleanupPlan plan, String operator, String shopName) {
        if (plan == null || !plan.deleteMedia()) {
            return new CleanupResult(List.of(), List.of(), List.of());
        }

        Set<Path> deletable = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        for (Path candidate : plan.candidatePaths()) {
            if (isReferencedByPersistedCanvas(candidate)) {
                skipped.add(candidate.getFileName().toString());
            } else {
                deletable.add(candidate);
            }
        }

        List<CanvasTask> ownedTasks = canvasTaskRepository
                .findByShopNameAndOperatorOrderByUpdatedAtDesc(shopName, operator);
        List<CanvasTask> tasksToDelete = ownedTasks.stream()
                .filter(task -> plan.taskIds().contains(text(task.getTaskId()))
                        || (task.getLocalPath() != null && deletable.contains(normalizePath(task.getLocalPath()))))
                .toList();
        List<String> removedTaskIds = tasksToDelete.stream()
                .map(CanvasTask::getTaskId)
                .filter(Objects::nonNull)
                .toList();
        if (!tasksToDelete.isEmpty()) canvasTaskRepository.deleteAll(tasksToDelete);

        List<String> removedFiles = new ArrayList<>();
        for (Path path : deletable) {
            try {
                if (Files.deleteIfExists(path)) removedFiles.add(path.getFileName().toString());
            } catch (Exception e) {
                skipped.add(path.getFileName().toString());
                log.warn("[AI Canvas] failed to delete unreferenced media: path={}, error={}", path, e.getMessage());
            }
        }
        return new CleanupResult(removedFiles, distinct(skipped), removedTaskIds);
    }

    Set<Path> generatedPaths(Object value) {
        Set<String> urls = new LinkedHashSet<>();
        collectLocalMediaUrls(value, urls);
        Set<Path> paths = new LinkedHashSet<>();
        for (String url : urls) {
            Path path = generatedPathFromUrl(url);
            if (path != null) paths.add(path);
        }
        return paths;
    }

    Path generatedPathFromUrl(String rawUrl) {
        String value = text(rawUrl);
        if (!value.startsWith("/ai-result/")) return null;
        try {
            String relative = URLDecoder.decode(
                    value.substring("/ai-result/".length()).split("[?#]", 2)[0], StandardCharsets.UTF_8);
            Path root = localSaveRoot();
            Path generatedRoot = root.resolve("canvas").normalize();
            Path resolved = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
            if (!resolved.startsWith(generatedRoot) || resolved.equals(generatedRoot)) return null;
            return resolved;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void expandOwnedResultPaths(Map<String, Object> canvas, Set<Path> paths) {
        boolean changed;
        do {
            changed = false;
            for (Map<String, Object> node : mapList(canvas.get("nodes"))) {
                String type = text(node.get("type")).toLowerCase(Locale.ROOT);
                List<Object> owned = new ArrayList<>();
                if ("output".equals(type)) {
                    owned.addAll(objectList(node.get("images")));
                } else if ("smart-image".equals(type)) {
                    for (Object item : objectList(node.get("images"))) {
                        if (isGeneratedSmartResult(item) || referencesAnyPath(item, paths)) owned.add(item);
                    }
                }
                if (!owned.isEmpty() && owned.stream().anyMatch(item -> referencesAnyPath(item, paths))) {
                    int before = paths.size();
                    paths.addAll(generatedPaths(owned));
                    changed |= paths.size() != before;
                }
            }
        } while (changed);
    }

    private List<String> resetResultNodes(Map<String, Object> canvas, Set<Path> paths) {
        List<String> resetIds = new ArrayList<>();
        List<Map<String, Object>> nodes = mapList(canvas.get("nodes"));
        for (Map<String, Object> node : nodes) {
            String type = text(node.get("type")).toLowerCase(Locale.ROOT);
            boolean changed = false;

            if (node.get("generatedOutputs") instanceof List<?>) {
                List<Object> outputs = objectList(node.get("generatedOutputs"));
                List<Object> kept = outputs.stream().filter(item -> !referencesAnyPath(item, paths)).toList();
                if (kept.size() != outputs.size()) {
                    node.put("generatedOutputs", new ArrayList<>(kept));
                    changed = true;
                }
            }

            if (("output".equals(type) || "smart-image".equals(type)) && node.get("images") instanceof List<?>) {
                List<Object> images = objectList(node.get("images"));
                List<Object> kept = images.stream()
                        .filter(item -> !referencesAnyPath(item, paths))
                        .toList();
                if (kept.size() != images.size()) {
                    node.put("images", new ArrayList<>(kept));
                    changed = true;
                    if ("output".equals(type)) {
                        node.put("_pending", new ArrayList<>());
                        node.put("imageComparisons", new LinkedHashMap<>());
                    } else {
                        clearSmartRunState(node);
                    }
                }
            } else if ("image".equals(type) && referencesAnyPath(node.get("url"), paths)) {
                node.put("url", "");
                node.put("mediaKind", "image");
                node.put("name", "空白图片");
                changed = true;
            }

            if (changed && !text(node.get("id")).isBlank()) resetIds.add(text(node.get("id")));
        }
        canvas.put("nodes", nodes);
        return distinct(resetIds);
    }

    private boolean isGeneratedSmartResult(Object value) {
        if (!(value instanceof Map<?, ?> map)) return false;
        return Boolean.TRUE.equals(map.get("generatedResult")) && !Boolean.TRUE.equals(map.get("loopInputPreview"));
    }

    private void clearSmartRunState(Map<String, Object> node) {
        node.put("pending", 0);
        node.put("running", false);
        node.put("queued", false);
        for (String key : List.of("jimengPending", "pendingTasks", "runStartedAt", "runFinishedAt",
                "runElapsedMs", "runTimerHidden", "outputKind", "w", "h")) {
            node.remove(key);
        }
    }

    private boolean isReferencedByPersistedCanvas(Path target) {
        for (CanvasProject project : canvasProjectRepository.findAll()) {
            String snapshot = project.getSnapshotJson();
            if (snapshot == null || snapshot.isBlank()) continue;
            try {
                Object value = objectMapper.readValue(snapshot, Object.class);
                if (referencesAnyPath(value, Set.of(target))) return true;
            } catch (Exception e) {
                // Safety first: an unreadable owner must keep the media.
                log.warn("[AI Canvas] keeping media because a canvas snapshot could not be scanned: projectId={}", project.getId());
                return true;
            }
        }
        return false;
    }

    private boolean referencesAnyPath(Object value, Collection<Path> paths) {
        if (value == null || paths.isEmpty()) return false;
        if (value instanceof String string) {
            Path resolved = generatedPathFromUrl(string);
            return resolved != null && paths.contains(resolved);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(item -> referencesAnyPath(item, paths));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> referencesAnyPath(item, paths));
        }
        return false;
    }

    private void collectLocalMediaUrls(Object value, Set<String> urls) {
        if (value instanceof String string) {
            if (string.trim().startsWith("/ai-result/")) urls.add(string.trim());
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectLocalMediaUrls(item, urls));
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectLocalMediaUrls(item, urls));
        }
    }

    private void collectTaskIds(Object value, Set<String> taskIds) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                String normalizedKey = String.valueOf(key).replace("-", "_").toLowerCase(Locale.ROOT);
                if (TASK_ID_KEYS.contains(normalizedKey) && item != null && !text(item).isBlank()) taskIds.add(text(item));
                collectTaskIds(item, taskIds);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collectTaskIds(item, taskIds));
        }
    }

    private Path localSaveRoot() {
        String configured = appProperties.getLocalSaveRoot();
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    ? "D:/AiResult" : "/tmp/ai-result";
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private Path normalizePath(String value) {
        try {
            return Paths.get(value).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    result.add(objectMapper.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {}));
                }
            }
        }
        return result;
    }

    private List<Object> objectList(Object value) {
        return value instanceof Collection<?> collection ? new ArrayList<>(collection) : new ArrayList<>();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T> List<T> distinct(List<T> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    public record CleanupPlan(Map<String, Object> canvas,
                              Set<Path> candidatePaths,
                              Set<String> taskIds,
                              List<String> resetNodeIds,
                              boolean deleteMedia) {
    }

    public record CleanupResult(List<String> removedFiles,
                                List<String> skippedReferenced,
                                List<String> removedTaskIds) {
    }
}
