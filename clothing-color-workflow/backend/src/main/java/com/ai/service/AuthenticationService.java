package com.ai.service;

import com.ai.entity.SysUser;
import com.ai.entity.UserSession;
import com.ai.repository.SysUserRepository;
import com.ai.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    public static final String ACTIVE = "ACTIVE";
    public static final String LOCKED = "LOCKED";
    public static final String DISABLED = "DISABLED";

    private final SysUserRepository sysUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final SecurityAuditService securityAuditService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.login.max-failed-attempts:10}")
    private int maxFailedAttempts;

    @Value("${app.security.session-hours:12}")
    private int sessionHours;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateLegacyPasswords() {
        int migrated = 0;
        int disabled = 0;
        for (SysUser user : sysUserRepository.findAll()) {
            String stored = user.getPassword();
            if (stored == null || stored.isBlank()) {
                user.setStatus(DISABLED);
                disabled++;
            } else if (!isEncoded(stored)) {
                user.setPassword(passwordEncoder.encode(stored));
                migrated++;
            }
        }
        if (migrated + disabled > 0) {
            sysUserRepository.flush();
            log.info("[Security] legacy passwords migrated: hashed={}, disabledEmptyPassword={}", migrated, disabled);
        }
    }

    @Transactional
    public LoginResult login(String username, String password, String ipAddress, String userAgent) {
        String attemptedUsername = normalize(username);
        String attemptedPassword = password == null ? "" : password;
        SysUser user = attemptedUsername.isBlank() ? null
                : sysUserRepository.findByUsernameForUpdate(attemptedUsername).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        if (user == null) {
            recordLogin(null, attemptedUsername, false, "BAD_CREDENTIALS", 0, ipAddress, userAgent);
            return LoginResult.failed("账号或密码错误");
        }

        String status = user.getStatus();
        if (DISABLED.equalsIgnoreCase(status)) {
            recordLogin(user, attemptedUsername, false, "DISABLED", user.getFailedLoginCount(), ipAddress, userAgent);
            return LoginResult.failed("账号不可用，请联系管理员");
        }

        if (LOCKED.equalsIgnoreCase(status)) {
            recordLogin(user, attemptedUsername, false, "LOCKED", user.getFailedLoginCount(), ipAddress, userAgent);
            return LoginResult.failed(lockMessage());
        }

        if (!matches(attemptedPassword, user.getPassword())) {
            int attemptLimit = Math.max(1, maxFailedAttempts);
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            user.setLastFailedLoginAt(now);
            String resultCode = "BAD_CREDENTIALS";
            int remainingAttempts = Math.max(0, attemptLimit - failures);
            String message = failedAttemptMessage(remainingAttempts, attemptLimit);
            if (failures >= attemptLimit) {
                user.setStatus(LOCKED);
                user.setLockedAt(now);
                user.setLockedUntil(null);
                userSessionRepository.deleteByUser_Id(user.getId());
                resultCode = "LOCKED_AFTER_FAILURES";
                message = lockMessage();
            }
            sysUserRepository.save(user);
            recordLogin(user, attemptedUsername, false, resultCode, failures, ipAddress, userAgent);
            return LoginResult.failed(message);
        }

        if (!isEncoded(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(attemptedPassword));
        }
        resetLock(user);
        user.setLastLoginAt(now);
        sysUserRepository.save(user);

        String token = newToken();
        userSessionRepository.save(UserSession.create(user, token,
                now.plusHours(Math.max(1, sessionHours))));
        user.setToken(token);
        recordLogin(user, attemptedUsername, true, "SUCCESS", 0, ipAddress, userAgent);
        return LoginResult.succeeded(user);
    }

    @Transactional
    public ProfileResult updateProfile(String token, String oldPassword, String newPassword, String newRealName) {
        UserSession session = userSessionRepository.findByTokenWithUser(token).orElse(null);
        if (session == null) return ProfileResult.failed("用户不存在或登录已失效");
        SysUser user = session.getUser();
        boolean updated = false;

        if (newPassword != null && !newPassword.isBlank()) {
            if (!matches(oldPassword == null ? "" : oldPassword.trim(), user.getPassword())) {
                return ProfileResult.failed("修改失败：旧密码不正确");
            }
            String password = newPassword.trim();
            if (password.length() < 8) return ProfileResult.failed("新密码至少需要8位");
            user.setPassword(passwordEncoder.encode(password));
            resetLock(user);
            userSessionRepository.deleteByUser_Id(user.getId());
            String replacementToken = newToken();
            userSessionRepository.save(UserSession.create(user, replacementToken,
                    LocalDateTime.now().plusHours(Math.max(1, sessionHours))));
            user.setToken(replacementToken);
            updated = true;
        }
        if (newRealName != null && !newRealName.isBlank()) {
            user.setRealName(newRealName.trim());
            updated = true;
        }
        if (updated) sysUserRepository.save(user);
        return ProfileResult.succeeded(user, updated ? "修改成功" : "没有需要修改的内容");
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        userSessionRepository.findByTokenWithUser(token.trim()).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            userSessionRepository.save(session);
        });
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean isPasswordStrongEnough(String password) {
        return password != null && password.trim().length() >= 8;
    }

    public void resetLock(SysUser user) {
        user.setStatus(ACTIVE);
        user.setFailedLoginCount(0);
        user.setLockedAt(null);
        user.setLockedUntil(null);
        user.setLastFailedLoginAt(null);
    }

    private boolean matches(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        if (isEncoded(storedPassword)) return passwordEncoder.matches(rawPassword, storedPassword);
        return MessageDigest.isEqual(rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isEncoded(String value) {
        return value != null && value.matches("^\\$2[aby]\\$.*");
    }

    private String failedAttemptMessage(int remainingAttempts, int attemptLimit) {
        return "账号或密码错误，还可尝试 " + remainingAttempts + " 次。连续输错 "
                + attemptLimit + " 次后账号将被锁定，需联系管理员解封。";
    }

    private String lockMessage() {
        return "密码连续输错次数已达上限，账号已锁定，请联系管理员解封。";
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void recordLogin(SysUser user, String username, boolean success, String resultCode,
                             int failedCount, String ipAddress, String userAgent) {
        try {
            securityAuditService.recordLogin(user, username, success, resultCode, failedCount, ipAddress, userAgent);
        } catch (Exception ignored) {
            // Authentication must remain available if the audit sink is temporarily unavailable.
        }
    }

    public record LoginResult(boolean success, String message, SysUser user) {
        static LoginResult succeeded(SysUser user) {
            return new LoginResult(true, "登录成功", user);
        }

        static LoginResult failed(String message) {
            return new LoginResult(false, message, null);
        }
    }

    public record ProfileResult(boolean success, String message, SysUser user) {
        static ProfileResult succeeded(SysUser user, String message) {
            return new ProfileResult(true, message, user);
        }

        static ProfileResult failed(String message) {
            return new ProfileResult(false, message, null);
        }
    }
}
