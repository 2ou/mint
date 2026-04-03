package com.ai.creative.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class CodeGenUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private CodeGenUtils() {
    }

    public static String code(String prefix) {
        return prefix + FORMATTER.format(LocalDateTime.now()) + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
