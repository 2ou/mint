package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "color_card")
public class ColorCard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String fabricType; // 面料类型 (例: 红根-666气流冰丝皱印花)

    @Column
    private Integer printType; // 印花类型 (1: 纯色, 2: 印花)

    @Column(length = 100)
    private String patternName; // 花型名称 (例: 黑底灰蝴蝶21#)

    @Column(length = 50)
    private String patternCode; // 花型编号 (例: 21#)

    @Column(length = 500)
    private String colorCardUrl; // 色卡图 (OSS 链接)

    @Column(length = 500)
    private String exampleUrl; // 案例图 (OSS 链接)

    @CreationTimestamp
    private LocalDateTime createdAt; // 创建时间

    @UpdateTimestamp
    private LocalDateTime updatedAt; // 更新时间
}