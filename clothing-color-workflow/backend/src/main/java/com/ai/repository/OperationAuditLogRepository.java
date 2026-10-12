package com.ai.repository;

import com.ai.entity.OperationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Long> {
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
