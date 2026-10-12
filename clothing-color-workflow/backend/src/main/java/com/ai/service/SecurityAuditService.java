package com.ai.service;

import com.ai.entity.LoginAuditLog;
import com.ai.entity.OperationAuditLog;
import com.ai.entity.SysUser;
import com.ai.repository.LoginAuditLogRepository;
import com.ai.repository.OperationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    private final LoginAuditLogRepository loginAuditLogRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;

    @Value("${app.security.audit-retention-days:180}")
    private int retentionDays;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(SysUser user, String attemptedUsername, boolean success, String resultCode,
                            int failedCount, String ipAddress, String userAgent) {
        LoginAuditLog row = new LoginAuditLog();
        if (user != null) {
            row.setUserId(user.getId());
            row.setShopName(user.getShopName());
            row.setOperator(user.getRealName());
        }
        row.setUsername(attemptedUsername);
        row.setSuccess(success);
        row.setResultCode(resultCode);
        row.setFailedCount(failedCount);
        row.setIpAddress(ipAddress);
        row.setUserAgent(userAgent);
        loginAuditLogRepository.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOperation(Long userId, String username, String shopName, String operator,
                                String method, String path, int statusCode, long durationMs,
                                String ipAddress, String userAgent, String errorMessage) {
        OperationAuditLog row = new OperationAuditLog();
        row.setUserId(userId);
        row.setUsername(username);
        row.setShopName(shopName);
        row.setOperator(operator);
        row.setHttpMethod(method);
        row.setRequestPath(path);
        row.setStatusCode(statusCode);
        row.setDurationMs(durationMs);
        row.setIpAddress(ipAddress);
        row.setUserAgent(userAgent);
        row.setErrorMessage(errorMessage);
        operationAuditLogRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<LoginAuditLog> recentLoginLogs(int limit) {
        return loginAuditLogRepository.findAll(PageRequest.of(0, safeLimit(limit),
                Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    @Transactional(readOnly = true)
    public List<OperationAuditLog> recentOperationLogs(int limit) {
        return operationAuditLogRepository.findAll(PageRequest.of(0, safeLimit(limit),
                Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    @Scheduled(cron = "${app.security.audit-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void cleanupExpiredAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(30, retentionDays));
        long loginRows = loginAuditLogRepository.deleteByCreatedAtBefore(cutoff);
        long operationRows = operationAuditLogRepository.deleteByCreatedAtBefore(cutoff);
        if (loginRows + operationRows > 0) {
            log.info("[Security Audit] expired logs deleted: login={}, operation={}, cutoff={}",
                    loginRows, operationRows, cutoff);
        }
    }

    private int safeLimit(int limit) {
        return Math.min(Math.max(limit, 1), 500);
    }
}
