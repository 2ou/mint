package com.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建 A+ 项目请求
 */
@Data
public class AplusProjectCreateRequest {
    private String projectName;
    private String spu;
    private String referenceImageUrl;
    private String sellingPoints;
    private List<String> selectedModules;
    /** 各模块补充信息，key 为模块编号如 "AD-02" */
    private Map<String, AplusModuleExtra> moduleExtras;
}
