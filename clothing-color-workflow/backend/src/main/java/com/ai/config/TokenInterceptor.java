package com.ai.config;

import com.ai.entity.SysUser;
import com.ai.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final UserSessionRepository userSessionRepository;

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

        // 🔴 去数据库真实校验这个 Token 是否有效
        SysUser user = userSessionRepository.findUserByToken(token);
        if (user == null) {
            return reject(response, "登录已失效，请重新登录");
        }

        // 🔴 将真实姓名和店铺名分别塞入 Request 域，方便后续分别落库
        request.setAttribute("operator", user.getRealName());
        request.setAttribute("shopName", user.getShopName());

        log.info("✅ [溯源] 店铺 [{}] 的员工 [{}] 发起了操作: {}", user.getShopName(), user.getRealName(), request.getRequestURI());
        return true;
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"success\":false, \"message\":\"%s\"}", msg));
        return false;
    }
}
