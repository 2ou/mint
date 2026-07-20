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
    private String errorMessage;

    @Column(length = 50)
    private String operator;

    @Column(length = 100)
    private String shopName;

    @Lob
    @Column(columnDefinition = "longtext")
    private String callbackPayloadJson;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
