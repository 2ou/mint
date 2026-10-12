package com.ai.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
public class SysUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(length = 100)
    private String password;
    private String shopName;
    private String realName;

    @Column(length = 16)
    private String status = "ACTIVE";

    private Integer failedLoginCount = 0;
    private LocalDateTime lockedAt;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastFailedLoginAt;
    private LocalDateTime lastLoginAt;

    // 仅用于登录接口返回给前端，不持久化到 sys_user 表
    @Transient
    private String token;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public String getStatus() {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }

    public int getFailedLoginCount() {
        return failedLoginCount == null ? 0 : failedLoginCount;
    }
}
