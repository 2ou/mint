package com.ai.service;

import com.ai.entity.SysUser;
import com.ai.entity.UserSession;
import com.ai.repository.SysUserRepository;
import com.ai.repository.UserSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    @Mock
    private SysUserRepository sysUserRepository;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private SecurityAuditService securityAuditService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AuthenticationService(sysUserRepository, userSessionRepository,
                securityAuditService, passwordEncoder);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 10);
        ReflectionTestUtils.setField(service, "sessionHours", 12);
    }

    @Test
    void successfulLegacyLoginMigratesPlaintextAndCreatesExpiringSession() {
        SysUser user = user("alice", "legacy-password");
        user.setFailedLoginCount(3);
        when(sysUserRepository.findByUsernameForUpdate("alice")).thenReturn(Optional.of(user));

        AuthenticationService.LoginResult result = service.login(
                " alice ", "legacy-password", "127.0.0.1", "JUnit");

        assertThat(result.success()).isTrue();
        assertThat(passwordEncoder.matches("legacy-password", user.getPassword())).isTrue();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(result.user().getToken()).isNotBlank();

        ArgumentCaptor<UserSession> session = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(session.capture());
        assertThat(session.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusHours(11));
        verify(securityAuditService).recordLogin(eq(user), eq("alice"), eq(true), eq("SUCCESS"),
                eq(0), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void tenthConsecutiveFailureLocksAccountAndRevokesSessions() {
        SysUser user = user("bob", passwordEncoder.encode("correct-password"));
        user.setFailedLoginCount(9);
        when(sysUserRepository.findByUsernameForUpdate("bob")).thenReturn(Optional.of(user));

        AuthenticationService.LoginResult result = service.login(
                "bob", "wrong-password", "10.0.0.8", "JUnit");

        assertThat(result.success()).isFalse();
        assertThat(user.getStatus()).isEqualTo(AuthenticationService.LOCKED);
        assertThat(user.getFailedLoginCount()).isEqualTo(10);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(result.message()).contains("管理员解封");
        verify(userSessionRepository).deleteByUser_Id(user.getId());
        verify(userSessionRepository, never()).save(any());
        verify(securityAuditService).recordLogin(eq(user), eq("bob"), eq(false),
                eq("LOCKED_AFTER_FAILURES"), eq(10), eq("10.0.0.8"), eq("JUnit"));
    }

    @Test
    void activeLockRejectsCorrectPassword() {
        SysUser user = user("carol", passwordEncoder.encode("correct-password"));
        user.setStatus(AuthenticationService.LOCKED);
        user.setFailedLoginCount(10);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(20));
        when(sysUserRepository.findByUsernameForUpdate("carol")).thenReturn(Optional.of(user));

        AuthenticationService.LoginResult result = service.login(
                "carol", "correct-password", "10.0.0.9", "JUnit");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("锁定");
        verify(userSessionRepository, never()).save(any());
    }

    @Test
    void failedLoginShowsRemainingAttemptsBeforePermanentLock() {
        SysUser user = user("erin", passwordEncoder.encode("correct-password"));
        user.setFailedLoginCount(8);
        when(sysUserRepository.findByUsernameForUpdate("erin")).thenReturn(Optional.of(user));

        AuthenticationService.LoginResult result = service.login(
                "erin", "wrong-password", "10.0.0.10", "JUnit");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("还可尝试 1 次", "管理员解封");
        assertThat(user.getStatus()).isEqualTo(AuthenticationService.ACTIVE);
        assertThat(user.getFailedLoginCount()).isEqualTo(9);
    }

    @Test
    void passwordIsWriteOnlyInJson() throws Exception {
        SysUser user = user("dave", "$2a$12$secret-hash");
        String json = new ObjectMapper().writeValueAsString(user);
        assertThat(json).doesNotContain("password", "secret-hash");
    }

    @Test
    void startupMigrationHashesLegacyPasswordsAndDisablesEmptyPasswords() {
        SysUser legacy = user("legacy", "plain-password");
        SysUser encoded = user("encoded", passwordEncoder.encode("already-secure"));
        SysUser empty = user("empty", " ");
        when(sysUserRepository.findAll()).thenReturn(List.of(legacy, encoded, empty));

        service.migrateLegacyPasswords();

        assertThat(passwordEncoder.matches("plain-password", legacy.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("already-secure", encoded.getPassword())).isTrue();
        assertThat(empty.getStatus()).isEqualTo(AuthenticationService.DISABLED);
        verify(sysUserRepository).flush();
    }

    private SysUser user(String username, String password) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername(username);
        user.setPassword(password);
        user.setShopName("SHOP-A");
        user.setRealName("Alice");
        user.setStatus(AuthenticationService.ACTIVE);
        return user;
    }
}
