package com.ai.config;

import com.ai.entity.SysUser;
import com.ai.entity.UserSession;
import com.ai.repository.UserSessionRepository;
import com.ai.service.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenInterceptorTest {
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private SecurityAuditService securityAuditService;

    private TokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TokenInterceptor(userSessionRepository, securityAuditService);
        ReflectionTestUtils.setField(interceptor, "sessionHours", 12);
    }

    @Test
    void validSessionAddsIdentityAndAuditsWriteOperation() throws Exception {
        UserSession session = session(LocalDateTime.now().plusHours(1));
        when(userSessionRepository.findByTokenWithUser("token-1")).thenReturn(Optional.of(session));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks");
        request.addHeader("X-User-Token", "token-1");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute("username")).isEqualTo("alice");
        assertThat(request.getAttribute("shopName")).isEqualTo("SHOP-A");
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(securityAuditService).recordOperation(eq(7L), eq("alice"), eq("SHOP-A"), eq("Alice"),
                eq("POST"), eq("/api/tasks"), eq(200), anyLong(), eq("127.0.0.1"), eq(""), eq(""));
    }

    @Test
    void expiredSessionIsRejectedAndRevoked() throws Exception {
        UserSession session = session(LocalDateTime.now().minusMinutes(1));
        when(userSessionRepository.findByTokenWithUser("expired")).thenReturn(Optional.of(session));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.addHeader("X-User-Token", "expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.getRevokedAt()).isNotNull();
        verify(userSessionRepository).save(session);
    }

    private UserSession session(LocalDateTime expiresAt) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setShopName("SHOP-A");
        user.setRealName("Alice");
        user.setStatus("ACTIVE");
        return UserSession.create(user, "token", expiresAt);
    }
}
