package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

/** A matchable model-price rule inside one version of the price catalogue. */
@Data
@Entity
@Table(name = "ai_model_price_rule")
public class ModelPriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ModelPriceVersion version;

    @Column(nullable = false, length = 32)
    private String provider = "kie";

    @Column(name = "media_type", nullable = false, length = 16)
    private String mediaType;

    @Column(nullable = false, length = 160)
    private String model;

    /** Blank means any resolution. */
    @Column(length = 24)
    private String resolution;

    /** Blank means any input. Supported values: text, image, video, multimodal. */
    @Column(name = "input_mode", length = 24)
    private String inputMode;

    /** PER_IMAGE, PER_TASK, or PER_SECOND. */
    @Column(name = "rate_unit", nullable = false, length = 24)
    private String rateUnit = "PER_TASK";

    @Column(name = "unit_price_cny", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPriceCny = BigDecimal.ZERO;

    @Column(name = "base_price_cny", nullable = false, precision = 12, scale = 4)
    private BigDecimal basePriceCny = BigDecimal.ZERO;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "display_name", length = 200)
    private String displayName;
}
