package com.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "operation_audit_log", indexes = {
        @Index(name = "idx_operation_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_operation_audit_user_id", columnList = "user_id")
})
public class OperationAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(length = 80)
    private String username;

    @Column(length = 80)
    private String shopName;

    @Column(length = 80)
    private String operator;

    @Column(length = 12)
    private String httpMethod;

    @Column(length = 512)
    private String requestPath;

    private Integer statusCode;
    private Long durationMs;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
