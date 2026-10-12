package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.entity.SysUser;
import com.ai.service.AuthenticationService;
import com.ai.util.SecurityRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ApiResponse<SysUser> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        AuthenticationService.LoginResult result = authenticationService.login(
                body.get("username"), body.get("password"),
                SecurityRequestUtils.clientIp(request), SecurityRequestUtils.userAgent(request));
        return result.success()
                ? ApiResponse.ok(result.message(), result.user())
                : ApiResponse.fail(result.message());
    }

    @PostMapping("/update-profile")
    public ApiResponse<SysUser> updateProfile(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String token = request.getHeader("X-User-Token");
        AuthenticationService.ProfileResult result = authenticationService.updateProfile(token,
                body.get("oldPassword"), body.get("password"), body.get("realName"));
        return result.success()
                ? ApiResponse.ok(result.message(), result.user())
                : ApiResponse.fail(result.message());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authenticationService.logout(request.getHeader("X-User-Token"));
        return ApiResponse.ok("已退出登录", null);
    }
}
