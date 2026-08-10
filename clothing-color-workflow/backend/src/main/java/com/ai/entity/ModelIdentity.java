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
@Table(name = "model_identity")
public class ModelIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false, unique = true)
    private String identityCode;

    @Column(length = 100, nullable = false)
    private String identityName;

    @Column(length = 50)
    private String modelType;

    @Column(length = 50)
    private String ethnicity;

    @Column(length = 20)
    private String ageRange;

    @Column(length = 200)
    private String bodyType;

    @Column(length = 80)
    private String hairstyle;

    @Column(length = 40)
    private String skinTone;

    @Column(length = 200)
    private String styleTags;

    @Lob
    private String identityPrompt;

    @Lob
    private String negativePrompt;

    @Column(length = 80)
    private String imageModel;

    @Column(length = 80)
    private String modelVersion;

    private Long seed;

    @Column(length = 500)
    private String referenceImageUrl;

    @Column(length = 32)
    private String referenceView;

    @Lob
    private String requiredViews;

    @Column(length = 20)
    private String status = "DRAFT";

    @Column(length = 32)
    private String storageStatus = "PENDING";

    @Column(length = 500)
    private String storageError;

    @Column(length = 50)
    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
