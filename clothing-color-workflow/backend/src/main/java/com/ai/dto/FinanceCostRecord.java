package com.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One successful generation task and the immutable fixed price captured at submission. */
public record FinanceCostRecord(
        String recordId,
        String source,
        Long sourceId,
        String taskId,
        String moduleCode,
        String moduleName,
        String shopName,
        String operator,
        String businessKey,
        String mediaType,
        String model,
        BigDecimal unitPrice,
        BigDecimal cost,
        boolean priced,
        LocalDateTime generatedAt
) {
}
