package com.ai.dto;

import java.time.LocalDate;

/** Filters shared by the finance dashboard and its full-result export. */
public record FinanceCostFilter(
        LocalDate startDate,
        LocalDate endDate,
        String module,
        String shopName,
        String operator,
        String mediaType,
        String model,
        String keyword
) {
}
