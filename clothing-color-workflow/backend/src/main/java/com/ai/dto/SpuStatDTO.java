package com.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor // 必须有全参构造供 JPA 封装返回结果
@NoArgsConstructor
public class SpuStatDTO {

    private String spu;

    // 🔴 核心修复：将 Double 改为 BigDecimal，完美匹配数据库实体类中的 cost 类型
    private BigDecimal totalCost;

}