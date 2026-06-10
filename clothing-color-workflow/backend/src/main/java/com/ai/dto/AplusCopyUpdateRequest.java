package com.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AplusCopyUpdateRequest {
    private String aplusMarkdown;
    private Map<String, String> moduleCopies;
}
