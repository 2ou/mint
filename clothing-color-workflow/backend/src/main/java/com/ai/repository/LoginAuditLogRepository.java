package com.ai.repository;

import com.ai.entity.LoginAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface LoginAuditLogRepository extends JpaRepository<LoginAuditLog, Long> {
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
