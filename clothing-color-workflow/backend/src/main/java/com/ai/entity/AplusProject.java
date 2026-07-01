package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A+ 套图项目
 */
@Data
@Entity
@Table(name = "aplus_project")
public class AplusProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目名称（用户自定义或自动生成） */
    @Column(nullable = false, length = 200)
    private String projectName;

    /** 产品 SPU 编号 */
    @Column(nullable = false, length = 100)
    private String spu;

    /** 产品参考图 OSS URL（KIE 图片模型以此作为产品款式真相） */
    @Column(nullable = false, length = 500)
    private String referenceImageUrl;

    /** 产品卖点（用户输入） */
    @Column(columnDefinition = "text")
    private String sellingPoints;

    /** AI 生成的 A+ MD 文档 */
    @Column(columnDefinition = "text")
    private String aplusMarkdown;

    /** Applied structure template id, if any. */
    private Long layoutTemplateId;

    /** Applied structure template name, if any. */
    @Column(length = 200)
    private String layoutTemplateName;

    /** Applied layout reference image URL, if any. */
    @Column(length = 500)
    private String layoutReferenceImageUrl;

    /** Applied parsed layout blueprint JSON, if any. */
    @Column(columnDefinition = "text")
    private String layoutBlueprintJson;

    /** 选择的模块列表，JSON 数组 ["AD-01","AD-03","AD-05"] */
    @Column(columnDefinition = "text")
    private String selectedModules;

    /** 项目状态：CREATED / GENERATING_COPY / COPY_DONE / GENERATING_IMAGES / COMPLETED / PARTIAL_FAILED / FAILED */
    @Column(nullable = false, length = 30)
    private String status;

    /** 错误信息 */
    @Column(columnDefinition = "text")
    private String errorMessage;

    /** 操作人 */
    @Column(length = 50)
    private String operator;

    /** 所属店铺 */
    @Column(length = 100)
    private String shopName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 关联的模块任务列表 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AplusImageTask> imageTasks;
}
