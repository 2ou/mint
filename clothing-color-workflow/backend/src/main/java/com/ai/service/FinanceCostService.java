package com.ai.service;

import com.ai.dto.FinanceCostFilter;
import com.ai.dto.FinanceCostRecord;
import com.ai.entity.CanvasTask;
import com.ai.entity.ImageTask;
import com.ai.repository.CanvasTaskRepository;
import com.ai.repository.ImageTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only platform cost view for Finance.
 *
 * <p>The accounting rule is deliberately simple: a successful task contributes exactly the
 * fixed catalogue price captured when it was submitted. Provider-returned cost is ignored.
 * A+ and AI model-library tables are intentionally not queried.</p>
 */
@Service
@RequiredArgsConstructor
public class FinanceCostService {

    public static final String BATCH_COLOR = "BATCH_COLOR";
    public static final String SCENE_GENERATION = "SCENE_GENERATION";
    public static final String VIDEO_GENERATION = "VIDEO_GENERATION";
    public static final String AI_CANVAS = "AI_CANVAS";
    public static final String TEMPLATE_LAB = "TEMPLATE_LAB";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> MODULES = Map.of(
            BATCH_COLOR, "批量换色",
            SCENE_GENERATION, "场景生成",
            VIDEO_GENERATION, "视频生成",
            AI_CANVAS, "AI画布",
            TEMPLATE_LAB, "模板拼图实验室"
    );

    private final ImageTaskRepository imageTaskRepository;
    private final CanvasTaskRepository canvasTaskRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> report(FinanceCostFilter filter, int page, int size) {
        List<FinanceCostRecord> records = findRecords(filter);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        int from = Math.min((safePage - 1) * safeSize, records.size());
        int to = Math.min(from + safeSize, records.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary(records));
        result.put("module_breakdown", breakdown(records, FinanceCostRecord::moduleName));
        result.put("shop_breakdown", breakdown(records, row -> blank(row.shopName(), "未标记店铺")));
        result.put("operator_breakdown", breakdown(records, row -> blank(row.operator(), "未标记操作人")));
        result.put("daily_trend", dailyTrend(records));
        result.put("records", records.subList(from, to));
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("total", records.size());
        result.put("total_pages", Math.max(1, (records.size() + safeSize - 1) / safeSize));
        result.put("options", options(records));
        result.put("data_cutoff", LocalDateTime.now(BUSINESS_ZONE).format(DATE_TIME));
        result.put("accounting_rule", "仅成功任务计费；费用取任务提交时保存的固定价格；不读取服务商返回费用");
        result.put("excluded_modules", List.of("AI套图", "AI模特库"));
        return result;
    }

    @Transactional(readOnly = true)
    public void exportWorkbook(FinanceCostFilter filter, OutputStream outputStream) throws IOException {
        List<FinanceCostRecord> records = findRecords(filter);
        try (Workbook workbook = new XSSFWorkbook()) {
            WorkbookStyles styles = new WorkbookStyles(workbook);
            writeSummarySheet(workbook, styles, records, filter);
            writeBreakdownSheet(workbook, styles, "按模块", breakdown(records, FinanceCostRecord::moduleName));
            writeBreakdownSheet(workbook, styles, "按店铺", breakdown(records, row -> blank(row.shopName(), "未标记店铺")));
            writeBreakdownSheet(workbook, styles, "按操作人", breakdown(records, row -> blank(row.operator(), "未标记操作人")));
            writeDetailSheet(workbook, styles, records);
            workbook.write(outputStream);
        }
    }

    List<FinanceCostRecord> findRecords(FinanceCostFilter filter) {
        List<FinanceCostRecord> records = new ArrayList<>();
        imageTaskRepository.findAll(imageSpecification(filter)).stream()
                .filter(task -> "SUCCESS".equalsIgnoreCase(task.getStatus()))
                .map(this::fromImageTask)
                .filter(row -> matchesMappedFilters(row, filter))
                .forEach(records::add);
        canvasTaskRepository.findAll(canvasSpecification(filter)).stream()
                .filter(task -> "SUCCESS".equalsIgnoreCase(task.getStatus()))
                .map(this::fromCanvasTask)
                .filter(row -> matchesMappedFilters(row, filter))
                .forEach(records::add);
        records.sort(Comparator.comparing(FinanceCostRecord::generatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return records;
    }

    private Specification<ImageTask> imageSpecification(FinanceCostFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.upper(root.get("status")), "SUCCESS"));
            Expression<LocalDateTime> chargedAt = cb.coalesce(root.get("completeTime"), root.get("createdAt"));
            addDatePredicates(filter, chargedAt, predicates, cb);
            addCommonPredicates(filter, root.get("shopName"), root.get("operator"), null,
                    root.get("taskId"), root.get("spu"), predicates, cb);
            if (hasText(filter.model())) {
                String modelTerm = "%" + filter.model().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("model")), modelTerm),
                        cb.like(cb.lower(root.get("requestedModel")), modelTerm)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<CanvasTask> canvasSpecification(FinanceCostFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.upper(root.get("status")), "SUCCESS"));
            Expression<LocalDateTime> chargedAt = cb.coalesce(root.get("updatedAt"), root.get("createdAt"));
            addDatePredicates(filter, chargedAt, predicates, cb);
            addCommonPredicates(filter, root.get("shopName"), root.get("operator"), null,
                    root.get("taskId"), root.get("canvasId"), predicates, cb);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addDatePredicates(FinanceCostFilter filter,
                                   Expression<LocalDateTime> chargedAt,
                                   List<Predicate> predicates,
                                   jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (filter.startDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(chargedAt, filter.startDate().atStartOfDay()));
        }
        if (filter.endDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(chargedAt, filter.endDate().atTime(LocalTime.MAX)));
        }
    }

    private void addCommonPredicates(FinanceCostFilter filter,
                                     Expression<String> shop,
                                     Expression<String> operator,
                                     Expression<String> model,
                                     Expression<String> taskId,
                                     Expression<String> businessKey,
                                     List<Predicate> predicates,
                                     jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (hasText(filter.shopName())) predicates.add(cb.equal(shop, filter.shopName().trim()));
        if (hasText(filter.operator())) predicates.add(cb.equal(operator, filter.operator().trim()));
        if (model != null && hasText(filter.model())) {
            predicates.add(cb.like(cb.lower(model), "%" + filter.model().trim().toLowerCase(Locale.ROOT) + "%"));
        }
        if (hasText(filter.keyword())) {
            String term = "%" + filter.keyword().trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(taskId), term),
                    cb.like(cb.lower(businessKey), term),
                    cb.like(cb.lower(operator), term)
            ));
        }
    }

    private FinanceCostRecord fromImageTask(ImageTask task) {
        String moduleCode = switch (task.getTaskType() == null ? 0 : task.getTaskType()) {
            case 1 -> BATCH_COLOR;
            case 2 -> SCENE_GENERATION;
            case 3 -> VIDEO_GENERATION;
            default -> SCENE_GENERATION;
        };
        BigDecimal cost = normalizedCost(task.getCost());
        LocalDateTime generatedAt = task.getCompleteTime() != null ? task.getCompleteTime() : task.getCreatedAt();
        return new FinanceCostRecord(
                "image:" + task.getId(), "image_task", task.getId(), blank(task.getTaskId(), ""),
                moduleCode, MODULES.get(moduleCode), blank(task.getShopName(), ""), blank(task.getOperator(), ""),
                blank(task.getSpu(), ""), VIDEO_GENERATION.equals(moduleCode) ? "video" : "image",
                blank(task.getRequestedModel(), blank(task.getModel(), "")), cost, cost, cost != null, generatedAt
        );
    }

    private FinanceCostRecord fromCanvasTask(CanvasTask task) {
        boolean templateLab = hasText(task.getCanvasId())
                && task.getCanvasId().toLowerCase(Locale.ROOT).startsWith("template-lab:");
        String moduleCode = templateLab ? TEMPLATE_LAB : AI_CANVAS;
        BigDecimal cost = normalizedCost(task.getEstimatedCost());
        LocalDateTime generatedAt = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        return new FinanceCostRecord(
                "canvas:" + task.getId(), "ai_canvas_task", task.getId(), blank(task.getTaskId(), ""),
                moduleCode, MODULES.get(moduleCode), blank(task.getShopName(), ""), blank(task.getOperator(), ""),
                blank(task.getCanvasId(), ""), blank(task.getMediaType(), "image"), canvasModel(task),
                cost, cost, cost != null, generatedAt
        );
    }

    private boolean matchesMappedFilters(FinanceCostRecord row, FinanceCostFilter filter) {
        if (hasText(filter.module()) && !filter.module().trim().equalsIgnoreCase(row.moduleCode())) return false;
        if (hasText(filter.mediaType()) && !filter.mediaType().trim().equalsIgnoreCase(row.mediaType())) return false;
        if (hasText(filter.model()) && !containsIgnoreCase(row.model(), filter.model())) return false;
        if (hasText(filter.keyword())) {
            String term = filter.keyword().trim();
            return containsIgnoreCase(row.taskId(), term)
                    || containsIgnoreCase(row.businessKey(), term)
                    || containsIgnoreCase(row.operator(), term);
        }
        return true;
    }

    private Map<String, Object> summary(List<FinanceCostRecord> records) {
        BigDecimal total = records.stream().map(FinanceCostRecord::cost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        long priced = records.stream().filter(FinanceCostRecord::priced).count();
        BigDecimal average = priced == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(priced), 4, RoundingMode.HALF_UP);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_cost", total);
        summary.put("successful_tasks", records.size());
        summary.put("priced_tasks", priced);
        summary.put("average_cost", average);
        summary.put("unpriced_tasks", records.size() - priced);
        return summary;
    }

    private List<Map<String, Object>> breakdown(List<FinanceCostRecord> records,
                                                Function<FinanceCostRecord, String> classifier) {
        Map<String, List<FinanceCostRecord>> groups = records.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()));
        return groups.entrySet().stream().map(entry -> {
                    BigDecimal cost = entry.getValue().stream().map(FinanceCostRecord::cost)
                            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.getKey());
                    row.put("cost", cost.setScale(4, RoundingMode.HALF_UP));
                    row.put("count", entry.getValue().size());
                    row.put("unpriced", entry.getValue().stream().filter(item -> !item.priced()).count());
                    return row;
                })
                .sorted((left, right) -> ((BigDecimal) right.get("cost")).compareTo((BigDecimal) left.get("cost")))
                .toList();
    }

    private List<Map<String, Object>> dailyTrend(List<FinanceCostRecord> records) {
        Map<LocalDate, List<FinanceCostRecord>> groups = records.stream()
                .filter(row -> row.generatedAt() != null)
                .collect(Collectors.groupingBy(row -> row.generatedAt().toLocalDate()));
        return groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey().toString());
            row.put("cost", entry.getValue().stream().map(FinanceCostRecord::cost)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(4, RoundingMode.HALF_UP));
            row.put("count", entry.getValue().size());
            return row;
        }).toList();
    }

    private Map<String, Object> options(List<FinanceCostRecord> records) {
        Map<String, Object> options = new LinkedHashMap<>();
        List<Map<String, String>> modules = MODULES.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> Map.of("value", entry.getKey(), "label", entry.getValue()))
                .toList();
        options.put("modules", modules);
        options.put("shops", distinct(records, FinanceCostRecord::shopName));
        options.put("operators", distinct(records, FinanceCostRecord::operator));
        options.put("models", distinct(records, FinanceCostRecord::model));
        options.put("media_types", List.of("image", "video"));
        return options;
    }

    private List<String> distinct(List<FinanceCostRecord> records, Function<FinanceCostRecord, String> getter) {
        Set<String> values = records.stream().map(getter).filter(FinanceCostService::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String canvasModel(CanvasTask task) {
        String fromSnapshot = jsonText(task.getPriceSnapshotJson(), "model");
        if (hasText(fromSnapshot)) return fromSnapshot;
        return jsonText(task.getRequestPayloadJson(), "model");
    }

    private String jsonText(String json, String field) {
        if (!hasText(json)) return "";
        try {
            JsonNode value = objectMapper.readTree(json).get(field);
            return value == null || value.isNull() ? "" : value.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private BigDecimal normalizedCost(BigDecimal cost) {
        return cost == null ? null : cost.setScale(4, RoundingMode.HALF_UP);
    }

    private static boolean containsIgnoreCase(String source, String term) {
        return source != null && term != null
                && source.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private void writeSummarySheet(Workbook workbook, WorkbookStyles styles,
                                   List<FinanceCostRecord> records, FinanceCostFilter filter) {
        Sheet sheet = workbook.createSheet("费用总览");
        Map<String, Object> summary = summary(records);
        int rowIndex = 0;
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "报表名称", "AI平台费用报表");
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "生成时间", LocalDateTime.now(BUSINESS_ZONE).format(DATE_TIME));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "统计周期", dateRangeText(filter));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "计费口径", "仅成功任务，采用任务提交时保存的固定价格");
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "排除模块", "AI套图、AI模特库");
        rowIndex++;
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "总费用（元）", summary.get("total_cost"));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "成功任务数", summary.get("successful_tasks"));
        writeKeyValue(sheet, styles, rowIndex, "平均单次费用（元）", summary.get("average_cost"));
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 54 * 256);
    }

    private int writeKeyValue(Sheet sheet, WorkbookStyles styles, int rowIndex, String key, Object value) {
        Row row = sheet.createRow(rowIndex);
        Cell keyCell = row.createCell(0);
        keyCell.setCellValue(key);
        keyCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        setCellValue(valueCell, value);
        valueCell.setCellStyle(value instanceof BigDecimal ? styles.money : styles.body);
        return rowIndex + 1;
    }

    private void writeBreakdownSheet(Workbook workbook, WorkbookStyles styles, String name,
                                     List<Map<String, Object>> rows) {
        Sheet sheet = workbook.createSheet(name);
        writeHeader(sheet.createRow(0), styles, List.of("名称", "费用（元）", "成功任务数"));
        int index = 1;
        for (Map<String, Object> item : rows) {
            Row row = sheet.createRow(index++);
            setCellValue(row.createCell(0), item.get("name"));
            setCellValue(row.createCell(1), item.get("cost"));
            row.getCell(1).setCellStyle(styles.money);
            setCellValue(row.createCell(2), item.get("count"));
        }
        sheet.createFreezePane(0, 1);
        setWidths(sheet, 28, 18, 16);
    }

    private void writeDetailSheet(Workbook workbook, WorkbookStyles styles, List<FinanceCostRecord> records) {
        Sheet sheet = workbook.createSheet("费用明细");
        writeHeader(sheet.createRow(0), styles, List.of(
                "生成时间", "模块", "店铺", "操作人", "业务编号", "媒体类型", "模型",
                "单价（元）", "费用（元）", "KIE任务ID"
        ));
        int index = 1;
        for (FinanceCostRecord item : records) {
            Row row = sheet.createRow(index++);
            setCellValue(row.createCell(0), item.generatedAt() == null ? "" : item.generatedAt().format(DATE_TIME));
            setCellValue(row.createCell(1), item.moduleName());
            setCellValue(row.createCell(2), item.shopName());
            setCellValue(row.createCell(3), item.operator());
            setCellValue(row.createCell(4), item.businessKey());
            setCellValue(row.createCell(5), item.mediaType());
            setCellValue(row.createCell(6), item.model());
            setCellValue(row.createCell(7), item.unitPrice());
            setCellValue(row.createCell(8), item.cost());
            row.getCell(7).setCellStyle(styles.money);
            row.getCell(8).setCellStyle(styles.money);
            setCellValue(row.createCell(9), item.taskId());
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, index - 1), 0, 9));
        setWidths(sheet, 21, 18, 18, 16, 24, 12, 34, 15, 15, 38);
    }

    private void writeHeader(Row row, WorkbookStyles styles, List<String> labels) {
        for (int i = 0; i < labels.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels.get(i));
            cell.setCellStyle(styles.header);
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void setWidths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
    }

    private String dateRangeText(FinanceCostFilter filter) {
        String start = filter.startDate() == null ? "不限" : filter.startDate().toString();
        String end = filter.endDate() == null ? "不限" : filter.endDate().toString();
        return start + " 至 " + end;
    }

    private static final class WorkbookStyles {
        private final CellStyle header;
        private final CellStyle label;
        private final CellStyle body;
        private final CellStyle money;

        private WorkbookStyles(Workbook workbook) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);

            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            label = workbook.createCellStyle();
            label.setFont(labelFont);
            label.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            label.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            body = workbook.createCellStyle();
            money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("¥#,##0.0000"));
        }
    }
}
