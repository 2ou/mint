package com.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor  // 🔴 加上这个，生成无参构造函数，解决 new 的报错
@AllArgsConstructor // 🔴 加上这个，配合 @Builder 使用
public class KieTaskResult {
    private boolean finished;
    private boolean success;
    private String status;
    private String taskId;
    private String resultUrl;
    /** All provider output URLs, for example Seedance's video plus requested final frame. */
    private List<String> resultUrls;
    private String errorMessage;
    private Long completeTime; // 🔴 对应 KIE 返回的 completeTime
    private BigDecimal cost; // KIE 返回的真实费用，用于画布计费显示（可为 null）
    private String localPath; // AI 画布结果本地落盘的绝对路径（不上 OSS），用于本地静态服务展示
}
