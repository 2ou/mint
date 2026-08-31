package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Internal price-catalogue row used to retain the price snapshot referenced by
 * historical tasks. The application exposes one current editable catalogue.
 */
@Data
@Entity
@Table(name = "ai_model_price_version")
public class ModelPriceVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false, unique = true, length = 64)
    private String versionCode;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "credit_to_cny", nullable = false, precision = 12, scale = 6)
    private BigDecimal creditToCny = new BigDecimal("0.032000");

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "published_by", length = 64)
    private String publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
