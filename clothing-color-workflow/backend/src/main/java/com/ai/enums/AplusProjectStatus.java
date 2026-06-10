package com.ai.enums;

public enum AplusProjectStatus {
    CREATED("已创建"),
    GENERATING_COPY("文案生成中"),
    COPY_DONE("文案待确认"),
    GENERATING_IMAGES("图片生成中"),
    COMPLETED("已完成"),
    PARTIAL_FAILED("部分失败"),
    FAILED("失败");

    private final String label;

    AplusProjectStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
