package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.entity.LoginAuditLog;
import com.ai.entity.OperationAuditLog;
import com.ai.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/security")
@RequiredArgsConstructor
public class SecurityAuditController {
    private final SecurityAuditService securityAuditService;

    @GetMapping("/login-logs")
    public ApiResponse<List<LoginAuditLog>> loginLogs(
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok("ok", securityAuditService.recentLoginLogs(limit));
    }

    @GetMapping("/operation-logs")
    public ApiResponse<List<OperationAuditLog>> operationLogs(
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok("ok", securityAuditService.recentOperationLogs(limit));
    }

    private void requireAdmin(HttpServletRequest request) {
        Object value = request.getAttribute("shopName");
        if (value == null || !"PINKSIR".equalsIgnoreCase(String.valueOf(value).trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅 PINKSIR 可以查看安全审计记录");
        }
    }
}
