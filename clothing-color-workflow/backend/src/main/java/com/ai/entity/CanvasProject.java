package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_canvas_project")
public class CanvasProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String projectName;

    @Column(length = 50)
    private String operator;

    @Column(length = 100)
    private String shopName;

    @Lob
    @Column(columnDefinition = "longtext")
    private String snapshotJson;

    @Lob
    @Column(columnDefinition = "longtext")
    private String metaJson;

    @Column(nullable = false)
    private Integer currentSnapshotVersion = 1;

    @Column(nullable = false)
    private Integer schemaVersion = 1;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
