package com.ai.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A+ 模块定义常量
 */
public class AplusModuleDefinition {

    /** 7 个模块的编号 → 名称映射 */
    public static final Map<String, String> MODULES = new LinkedHashMap<>();
    static {
        MODULES.put("AD-01", "品牌英雄图");
        MODULES.put("AD-02", "印花/面料故事图");
        MODULES.put("AD-03", "设计细节图");
        MODULES.put("AD-04", "多场景穿搭图");
        MODULES.put("AD-05", "舒适体验图");
        MODULES.put("AD-06", "尺码/版型指南");
        MODULES.put("AD-07", "护理/品牌收尾图");
    }

    /** 统一风格锚点 */
    public static final String STYLE_ANCHOR =
            "同一个产品、同一个印花、同一个面料、同一个品牌调性、同一个摄影风格、同一个排版体系、同一个色彩体系";

    /** 各模块视觉定位描述 */
    public static final Map<String, String> VISUAL_POSITIONS = new LinkedHashMap<>();
    static {
        VISUAL_POSITIONS.put("AD-01", "宽幅生活方式横幅 — 产品在目标环境中的整体气质");
        VISUAL_POSITIONS.put("AD-02", "左右分栏 — 左侧平铺产品/面料图案，右侧面料微距细节");
        VISUAL_POSITIONS.put("AD-03", "中心+四周 — 中心产品平铺图 + 四周圆形细节放大图和连接线");
        VISUAL_POSITIONS.put("AD-04", "三宫格 — Beach Day / Brunch Ready / Resort Evening");
        VISUAL_POSITIONS.put("AD-05", "主角+角落 — 主画面模特放松场景 + 角落面料微距小图");
        VISUAL_POSITIONS.put("AD-06", "技术图表 — 正反面平铺服装 + 量体箭头 + 尺码表");
        VISUAL_POSITIONS.put("AD-07", "静物+说明 — 折叠产品静物图 + 护理说明区域");
    }

    /** 各模块补充信息提示（前端 placeholder 用） */
    public static final Map<String, String> EXTRA_HINTS = new LinkedHashMap<>();
    static {
        EXTRA_HINTS.put("AD-01", "品牌调性、目标人群描述");
        EXTRA_HINTS.put("AD-02", "面料成分、工艺说明");
        EXTRA_HINTS.put("AD-03", "设计亮点（口袋/拉链/缝线）");
        EXTRA_HINTS.put("AD-04", "场景描述（海滩/brunch/晚宴）");
        EXTRA_HINTS.put("AD-05", "穿着感受描述");
        EXTRA_HINTS.put("AD-06", "具体尺码数据（S/M/L/XL）");
        EXTRA_HINTS.put("AD-07", "护理说明（洗涤方式、注意事项）");
    }
}
