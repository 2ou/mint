package com.ai.enums;

public enum AplusTaskStatus {
    PENDING("待生成"),
    PROCESSING("生成中"),
    SUCCESS("已完成"),
    FAILED("失败");

    private final String label;

    AplusTaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
