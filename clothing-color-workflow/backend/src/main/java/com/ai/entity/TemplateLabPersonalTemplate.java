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
@Table(name = "template_lab_personal_template", indexes = {
        @Index(name = "idx_template_lab_personal_owner_updated", columnList = "ownerUserId,updatedAt")
})
public class TemplateLabPersonalTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 500)
    private String description;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")
    private String definitionJson;

    @Lob
    @Column(columnDefinition = "longtext")
    private String thumbnailDataUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
