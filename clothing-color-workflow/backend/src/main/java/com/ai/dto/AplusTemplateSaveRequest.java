package com.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 保存 A+ 模板请求
 */
@Data
public class AplusTemplateSaveRequest {
    /** 模板名称 */
    private String templateName;
    /** 产品 SPU 编号（可选） */
    private String spu;
    /** 产品参考图 OSS URL */
    private String referenceImageUrl;
    /** 产品卖点 */
    private String sellingPoints;
    /** 选择的模块列表 */
    private List<String> selectedModules;
    /** 各模块补充信息，key 为模块编号如 "AD-02" */
    private Map<String, AplusModuleExtra> moduleExtras;
}
