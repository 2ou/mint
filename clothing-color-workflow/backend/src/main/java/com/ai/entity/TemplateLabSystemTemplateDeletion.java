package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "template_lab_system_template_deletion", uniqueConstraints = {
        @UniqueConstraint(name = "uk_template_lab_system_template_id", columnNames = "templateId")
})
public class TemplateLabSystemTemplateDeletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String templateId;

    @Column(nullable = false)
    private Long deletedByUserId;

    @CreationTimestamp
    private LocalDateTime deletedAt;
}
