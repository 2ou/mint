package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.entity.SysUser;
import com.ai.entity.UserSession;
import com.ai.repository.SysUserRepository;
import com.ai.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
public class AuthController {

    private final SysUserRepository sysUserRepository;
    private final UserSessionRepository userSessionRepository;

    @PostMapping("/login")
    public ApiResponse<SysUser> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        SysUser user = sysUserRepository.findByUsernameAndPassword(username, password);
        if (user == null) {
            return ApiResponse.fail("账号或密码错误");
        }

        // A session belongs to this login only. Logging in from another device
        // creates another row instead of invalidating this token.
        String token = UUID.randomUUID().toString().replace("-", "");
        userSessionRepository.save(UserSession.create(user, token));

        user.setToken(token);
        user.setPassword(null);
        return ApiResponse.ok("登录成功", user);
    }

    @PostMapping("/update-profile")
    public ApiResponse<SysUser> updateProfile(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String token = request.getHeader("X-User-Token");
        SysUser user = userSessionRepository.findUserByToken(token);

        if (user == null) {
            return ApiResponse.fail("用户不存在或登录已失效");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("password");
        String newRealName = body.get("realName");

        boolean updated = false;
        if (newPassword != null && !newPassword.isBlank()) {
            if (oldPassword == null || !oldPassword.trim().equals(user.getPassword())) {
                return ApiResponse.fail("修改失败：旧密码不正确");
            }
            user.setPassword(newPassword.trim());
            updated = true;
        }
        if (newRealName != null && !newRealName.isBlank()) {
            user.setRealName(newRealName.trim());
            updated = true;
        }
        if (updated) {
            sysUserRepository.save(user);
        }

        user.setPassword(null);
        return ApiResponse.ok("修改成功", user);
    }
}
