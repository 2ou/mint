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
    /** 产品参考图 URL（KIE 图片模型以此作为产品款式真相） */
    private String referenceImageUrl;
    private String sellingPoints;
    /** GPT 5.6 文本模型：gpt-5.6-sol / gpt-5.6-terra / gpt-5.6-luna */
    private String textModel;
    private String imageModel;
    private String resolution;
    private Long layoutTemplateId;
    private String layoutTemplateName;
    private String layoutReferenceImageUrl;
    private String layoutBlueprintJson;
    private List<String> selectedModules;
    /** 各模块补充信息，key 为模块编号如 "AD-02" */
    private Map<String, AplusModuleExtra> moduleExtras;
}
