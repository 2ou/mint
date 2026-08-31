package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.entity.CanvasProject;
import com.ai.entity.CanvasTask;
import com.ai.repository.CanvasProjectRepository;
import com.ai.repository.CanvasTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanvasMediaCleanupServiceTest {

    @TempDir
    Path saveRoot;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CanvasProjectRepository canvasProjectRepository;
    private CanvasTaskRepository canvasTaskRepository;
    private CanvasMediaCleanupService service;

    @BeforeEach
    void setUp() {
        canvasProjectRepository = mock(CanvasProjectRepository.class);
        canvasTaskRepository = mock(CanvasTaskRepository.class);
        AppProperties properties = new AppProperties();
        properties.setLocalSaveRoot(saveRoot.toString());
        service = new CanvasMediaCleanupService(
                canvasProjectRepository, canvasTaskRepository, objectMapper, properties);
    }

    @Test
    void fullCleanupResetsResultNodesAndDeletesUnreferencedFileAndTask() throws Exception {
        String url = "/ai-result/canvas/task-1.png";
        Path generated = saveRoot.resolve("canvas/task-1.png");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "generated");

        Map<String, Object> canvas = canvasWithGeneration("log-1", "task-1", url);
        CanvasMediaCleanupService.CleanupPlan plan = service.prepare(canvas, "log-1", true, true);

        assertThat(castList(plan.canvas().get("logs"))).isEmpty();
        assertThat(plan.resetNodeIds()).containsExactlyInAnyOrder("generator-1", "output-1");
        assertThat(castList(plan.canvas().get("connections"))).hasSize(1);
        Map<String, Object> generator = node(plan.canvas(), "generator-1");
        Map<String, Object> output = node(plan.canvas(), "output-1");
        assertThat(castList(generator.get("generatedOutputs"))).isEmpty();
        assertThat(generator.get("prompt")).isEqualTo("保留提示词");
        assertThat(castList(output.get("images"))).isEmpty();

        CanvasProject persisted = new CanvasProject();
        persisted.setId(1L);
        persisted.setSnapshotJson(objectMapper.writeValueAsString(plan.canvas()));
        when(canvasProjectRepository.findAll()).thenReturn(List.of(persisted));

        CanvasTask task = new CanvasTask();
        task.setTaskId("task-1");
        task.setLocalPath(generated.toString());
        when(canvasTaskRepository.findByShopNameAndOperatorOrderByUpdatedAtDesc("shop", "user"))
                .thenReturn(List.of(task));

        CanvasMediaCleanupService.CleanupResult result = service.finish(plan, "user", "shop");

        assertThat(result.removedFiles()).containsExactly("task-1.png");
        assertThat(result.removedTaskIds()).containsExactly("task-1");
        assertThat(Files.exists(generated)).isFalse();
        verify(canvasTaskRepository).deleteAll(anyList());
    }

    @Test
    void keepsGeneratedFileWhenAnotherCanvasStillReferencesIt() throws Exception {
        String url = "/ai-result/canvas/shared.png";
        Path generated = saveRoot.resolve("canvas/shared.png");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "shared");

        CanvasMediaCleanupService.CleanupPlan plan = service.prepare(
                canvasWithGeneration("log-2", "task-2", url), "log-2", true, true);
        CanvasProject other = new CanvasProject();
        other.setId(2L);
        other.setSnapshotJson(objectMapper.writeValueAsString(Map.of(
                "nodes", List.of(Map.of("id", "input", "type", "input", "url", url)))));
        when(canvasProjectRepository.findAll()).thenReturn(List.of(other));
        when(canvasTaskRepository.findByShopNameAndOperatorOrderByUpdatedAtDesc("shop", "user"))
                .thenReturn(List.of());

        CanvasMediaCleanupService.CleanupResult result = service.finish(plan, "user", "shop");

        assertThat(result.removedFiles()).isEmpty();
        assertThat(result.skippedReferenced()).containsExactly("shared.png");
        assertThat(Files.exists(generated)).isTrue();
    }

    @Test
    void onlyAcceptsGeneratedFilesInsideCanvasResultDirectory() {
        assertThat(service.generatedPathFromUrl("/ai-result/canvas/ok.png"))
                .isEqualTo(saveRoot.resolve("canvas/ok.png").toAbsolutePath().normalize());
        assertThat(service.generatedPathFromUrl("/ai-result/../secret.txt")).isNull();
        assertThat(service.generatedPathFromUrl("/ai-result/canvas/%2e%2e/secret.txt")).isNull();
        assertThat(service.generatedPathFromUrl("https://example.com/image.png")).isNull();
    }

    private Map<String, Object> canvasWithGeneration(String logId, String taskId, String url) {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("url", url);
        media.put("task_id", taskId);
        Map<String, Object> generator = new LinkedHashMap<>();
        generator.put("id", "generator-1");
        generator.put("type", "generator");
        generator.put("prompt", "保留提示词");
        generator.put("generatedOutputs", new ArrayList<>(List.of(media)));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("id", "output-1");
        output.put("type", "output");
        output.put("images", new ArrayList<>(List.of(media)));
        output.put("_pending", new ArrayList<>(List.of(Map.of("task_id", taskId))));

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("id", logId);
        log.put("request", Map.of("task_id", taskId));
        log.put("outputs", new ArrayList<>(List.of(media)));

        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("nodes", new ArrayList<>(List.of(generator, output)));
        canvas.put("connections", new ArrayList<>(List.of(Map.of("from", "generator-1", "to", "output-1"))));
        canvas.put("logs", new ArrayList<>(List.of(log)));
        return canvas;
    }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) {
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> node(Map<String, Object> canvas, String id) {
        return ((List<Map<String, Object>>) canvas.get("nodes")).stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst()
                .orElseThrow();
    }
}
