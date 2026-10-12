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
@Table(name = "login_audit_log", indexes = {
        @Index(name = "idx_login_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_login_audit_username", columnList = "username")
})
public class LoginAuditLog {
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

    private boolean success;

    @Column(length = 32)
    private String resultCode;

    private Integer failedCount;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
