package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "ai_canvas_task", indexes = {
        @Index(name = "idx_canvas_task_task_id", columnList = "task_id", unique = true)
})
public class CanvasTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 128, unique = true)
    private String taskId;

    @Column(nullable = false, length = 24)
    private String mediaType;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(length = 1024)
    private String resultUrl;

    @Column(length = 1024)
    private String localPath; // AI 画布结果本地落盘绝对路径（仅本地，不上 OSS）

    @Column(length = 1024)
    private String errorMessage;

    @Column(length = 50)
    private String operator;

    @Column(length = 100)
    private String shopName;

    /** The canvas project that submitted this task. Kept after node/media deletion for audit. */
    @Column(name = "canvas_id", length = 32)
    private String canvasId;

    @Column(name = "canvas_node_id", length = 96)
    private String canvasNodeId;

    /** Immutable quote captured when the task is submitted. */
    @Column(name = "estimated_cost", precision = 12, scale = 4)
    private BigDecimal estimatedCost;

    /** Provider-confirmed amount. Null means the provider has not returned billing data yet. */
    @Column(name = "actual_cost", precision = 12, scale = 4)
    private BigDecimal actualCost;

    @Column(name = "price_version", length = 64)
    private String priceVersion;

    @Lob
    @Column(name = "price_snapshot_json", columnDefinition = "longtext")
    private String priceSnapshotJson;

    @Lob
    @Column(columnDefinition = "longtext")
    private String callbackPayloadJson;

    /**
     * A normalized canvas request is kept with the task so a user can retry it
     * after refreshing the page. It contains no provider secret.
     */
    @Lob
    @Column(columnDefinition = "longtext")
    private String requestPayloadJson;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
