package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "template_lab_project", indexes = {
        @Index(name = "idx_template_lab_owner_updated", columnList = "ownerUserId,updatedAt")
})
public class TemplateLabProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 100)
    private String operator;

    @Column(nullable = false, length = 100)
    private String shopName;

    @Column(nullable = false, length = 160)
    private String projectName;

    @Column(nullable = false, length = 80)
    private String templateId;

    @Column(nullable = false)
    private Integer canvasWidth;

    @Column(nullable = false)
    private Integer canvasHeight;

    @Lob
    @Column(columnDefinition = "longtext")
    private String designJson;

    @Lob
    @Column(columnDefinition = "longtext")
    private String thumbnailDataUrl;

    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
