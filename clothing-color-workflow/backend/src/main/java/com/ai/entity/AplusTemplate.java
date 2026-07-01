package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A+ 套图模板
 */
@Data
@Entity
@Table(name = "aplus_template")
public class AplusTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称 */
    @Column(nullable = false, length = 200)
    private String templateName;

    /** FORM_TEMPLATE / LAYOUT_REFERENCE. */
    @Column(length = 40)
    private String templateType;

    /** DRAFT / ACTIVE / DISABLED. */
    @Column(length = 30)
    private String templateStatus;

    /** 关联 SPU（可为空，通用模板不绑 SPU） */
    @Column(length = 100)
    private String spu;

    /** A+ 参考图 OSS URL（永久桶，用于版式/风格参考） */
    @Column(length = 500)
    private String referenceImageUrl;

    /** 产品卖点 */
    @Column(columnDefinition = "text")
    private String sellingPoints;

    /** 选择的模块列表，JSON 数组 ["AD-01","AD-03","AD-05"] */
    @Column(columnDefinition = "text")
    private String selectedModules;

    /** 各模块补充信息，JSON 对象 {"AD-02":{"supplementaryImageUrl":"...","supplementaryText":"..."}} */
    @Column(columnDefinition = "text")
    private String moduleExtras;

    /** Original A+ structure/reference image URL for layout-reference templates. */
    @Column(length = 500)
    private String layoutReferenceImageUrl;

    /** Parsed reusable layout blueprint JSON. */
    @Column(columnDefinition = "text")
    private String layoutBlueprintJson;

    /** 创建人 */
    @Column(length = 50)
    private String operator;

    /** 所属店铺 */
    @Column(length = 100)
    private String shopName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
