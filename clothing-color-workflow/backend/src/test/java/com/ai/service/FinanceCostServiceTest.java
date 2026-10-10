package com.ai.service;

import com.ai.dto.FinanceCostFilter;
import com.ai.entity.CanvasTask;
import com.ai.entity.ImageTask;
import com.ai.repository.CanvasTaskRepository;
import com.ai.repository.ImageTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceCostServiceTest {

    @Mock
    private ImageTaskRepository imageTaskRepository;

    @Mock
    private CanvasTaskRepository canvasTaskRepository;

    private FinanceCostService service;

    @BeforeEach
    void setUp() {
        service = new FinanceCostService(imageTaskRepository, canvasTaskRepository, new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportUsesFixedSubmissionPriceAndIgnoresProviderCost() {
        ImageTask image = imageTask(1L, "SUCCESS", 1, "0.2500", "SPU-1");
        ImageTask unpriced = imageTask(2L, "SUCCESS", 2, null, "SPU-2");
        ImageTask failed = imageTask(3L, "FAILED", 3, "99.0000", "SPU-3");

        CanvasTask canvas = canvasTask(10L, "SUCCESS", "canvas-1", "0.4400", "9.9900");
        CanvasTask template = canvasTask(11L, "SUCCESS", "template-lab:88", "0.2560", "8.8800");

        when(imageTaskRepository.findAll(any(Specification.class))).thenReturn(List.of(image, unpriced, failed));
        when(canvasTaskRepository.findAll(any(Specification.class))).thenReturn(List.of(canvas, template));

        Map<String, Object> report = service.report(emptyFilter(), 1, 50);
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");

        assertThat(summary.get("total_cost")).isEqualTo(new BigDecimal("0.9460"));
        assertThat(summary.get("successful_tasks")).isEqualTo(4);
        assertThat(summary.get("priced_tasks")).isEqualTo(3L);
        assertThat(summary.get("unpriced_tasks")).isEqualTo(1L);

        List<?> rows = (List<?>) report.get("records");
        assertThat(rows).hasSize(4);
        assertThat(rows.toString()).contains("AI画布", "模板拼图实验室", "批量换色", "场景生成");
        assertThat(rows.toString()).doesNotContain("9.9900", "8.8800", "SPU-3");
    }

    @Test
    void exportContainsSummaryBreakdownsAndFullDetail() throws Exception {
        when(imageTaskRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(imageTask(1L, "SUCCESS", 1, "0.2500", "SPU-1")));
        when(canvasTaskRepository.findAll(any(Specification.class))).thenReturn(List.of());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.exportWorkbook(emptyFilter(), output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheet("费用总览")).isNotNull();
            assertThat(workbook.getSheet("按模块")).isNotNull();
            assertThat(workbook.getSheet("按店铺")).isNotNull();
            assertThat(workbook.getSheet("按操作人")).isNotNull();
            assertThat(workbook.getSheet("费用明细").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("按操作人").getRow(0).getLastCellNum()).isEqualTo((short) 3);
            assertThat(workbook.getSheet("费用明细").getRow(0).getLastCellNum()).isEqualTo((short) 10);
            assertThat(workbook.getSheet("费用总览").getLastRowNum()).isEqualTo(8);
        }
    }

    private FinanceCostFilter emptyFilter() {
        return new FinanceCostFilter(null, null, null, null, null, null, null, null);
    }

    private ImageTask imageTask(Long id, String status, int taskType, String cost, String spu) {
        ImageTask task = new ImageTask();
        task.setId(id);
        task.setTaskId("image-task-" + id);
        task.setStatus(status);
        task.setTaskType(taskType);
        task.setCost(cost == null ? null : new BigDecimal(cost));
        task.setSpu(spu);
        task.setShopName("SHOP-A");
        task.setOperator("Alice");
        task.setRequestedModel("nano-banana-pro");
        task.setCompleteTime(LocalDateTime.of(2026, 10, 10, 10, id.intValue()));
        return task;
    }

    private CanvasTask canvasTask(Long id, String status, String canvasId, String fixedCost, String providerCost) {
        CanvasTask task = new CanvasTask();
        task.setId(id);
        task.setTaskId("canvas-task-" + id);
        task.setStatus(status);
        task.setCanvasId(canvasId);
        task.setShopName("SHOP-B");
        task.setOperator("Bob");
        task.setMediaType("image");
        task.setEstimatedCost(new BigDecimal(fixedCost));
        task.setActualCost(new BigDecimal(providerCost));
        task.setPriceSnapshotJson("{\"model\":\"gpt-image-2.5\"}");
        task.setUpdatedAt(LocalDateTime.of(2026, 10, 10, 11, id.intValue()));
        return task;
    }
}
