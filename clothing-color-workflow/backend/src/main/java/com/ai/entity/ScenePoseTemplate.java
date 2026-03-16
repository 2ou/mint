package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "scene_pose_template")
public class ScenePoseTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(length = 50, nullable = false)
    private String category; // 泳装场景、私服场景、家居服场景

    @Column(length = 255)
    private String sceneDesc; // 场景模块

    @Column(columnDefinition = "TEXT")
    private String poseDesc; // 动作手部模块

    @Column(length = 255)
    private String emotionDesc; // 表情氛围模块

    @Column(columnDefinition = "TEXT", nullable = false)
    private String basePrompt; // 完整主体提示词

    @Column(length = 500)
    private String exampleImageUrl; // 效果参考图

    @Column(length = 200)
    private String tags; // 检索标签

    @Column
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}