package com.ai.dto;

import lombok.Data;

@Data
public class SpuStatDTO {
    private String spu;
    private int count2K;     // 2K 成功数量
    private int count4K;     // 4K 成功数量
    private int taskCount;   // 总成功数量
    private double totalCost; // 消费总金额 (RMB)
}