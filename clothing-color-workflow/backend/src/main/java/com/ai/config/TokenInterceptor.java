package com.ai.config;

import com.ai.entity.SysUser;
import com.ai.entity.UserSession;
import com.ai.repository.UserSessionRepository;
import com.ai.service.AuthenticationService;
import com.ai.service.SecurityAuditService;
import com.ai.util.SecurityRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private static final String AUDIT_STARTED_AT = "securityAuditStartedAt";

    private final UserSessionRepository userSessionRepository;
    private final SecurityAuditService securityAuditService;

    @Value("${app.security.session-hours:12}")
    private int sessionHours;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true; // 放行预检
        
        // 允许登录接口直接访问，不拦截
        if (request.getRequestURI().contains("/api/auth/login")) return true;
        // 🔴 新增：放行流式透传下载接口，因为它没法带 Token
        if (request.getRequestURI().contains("/proxy-download")) return true;

        String requestUri = request.getRequestURI();
        if ("/api/download-output".equals(requestUri) || "/api/media-preview".equals(requestUri)) {
            return true;
        }

        String token = request.getHeader("X-User-Token");
        
        if (token == null || token.isEmpty()) {
            return reject(response, "请求头缺少 Token，请先登录");
        }

        UserSession session = userSessionRepository.findByTokenWithUser(token).orElse(null);
        if (session == null || !sessionIsActive(session)) {
            return reject(response, "登录已失效，请重新登录");
        }
        SysUser user = session.getUser();
        if (!AuthenticationService.ACTIVE.equalsIgnoreCase(user.getStatus())) {
            return reject(response, "账号已锁定或停用，请联系管理员");
        }

        // 🔴 将真实姓名和店铺名分别塞入 Request 域，方便后续分别落库
        request.setAttribute("operator", user.getRealName());
        request.setAttribute("shopName", user.getShopName());
        request.setAttribute("userId", user.getId());
        request.setAttribute("username", user.getUsername());
        if (shouldAudit(request)) request.setAttribute(AUDIT_STARTED_AT, System.nanoTime());

        log.info("✅ [溯源] 店铺 [{}] 的员工 [{}] 发起了操作: {}", user.getShopName(), user.getRealName(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object startedAt = request.getAttribute(AUDIT_STARTED_AT);
        if (!(startedAt instanceof Long startNanos)) return;
        try {
            long durationMs = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
            securityAuditService.recordOperation(
                    (Long) request.getAttribute("userId"),
                    stringAttribute(request, "username"),
                    stringAttribute(request, "shopName"),
                    stringAttribute(request, "operator"),
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs,
                    SecurityRequestUtils.clientIp(request), SecurityRequestUtils.userAgent(request),
                    ex == null ? "" : SecurityRequestUtils.truncate(ex.getMessage(), 500)
            );
        } catch (Exception auditError) {
            log.warn("[Security Audit] operation log write failed: {}", auditError.getMessage());
        }
    }

    private boolean sessionIsActive(UserSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null) return false;
        LocalDateTime expiresAt = session.getExpiresAt();
        if (expiresAt == null && session.getCreatedAt() != null) {
            expiresAt = session.getCreatedAt().plusHours(Math.max(1, sessionHours));
        }
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            session.setRevokedAt(now);
            userSessionRepository.save(session);
            return false;
        }
        return true;
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method))
                || path.startsWith("/api/admin/") || path.startsWith("/api/finance/");
    }

    private String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"success\":false, \"message\":\"%s\"}", msg));
        return false;
    }
}
