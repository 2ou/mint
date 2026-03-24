package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.entity.SysUser;
import com.ai.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
public class AuthController {

    private final SysUserRepository sysUserRepository;

    @PostMapping("/login")
    public ApiResponse<SysUser> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // 去数据库核对账号密码
        SysUser user = sysUserRepository.findByUsernameAndPassword(username, password);
        
        if (user == null) {
            return ApiResponse.fail("账号或密码错误");
        }

        // 登录成功，生成一个不重复的 Token，并更新到数据库
        String token = UUID.randomUUID().toString().replace("-", "");
        user.setToken(token);
        sysUserRepository.save(user);

        // 为了安全，不把密码返回给前端
        user.setPassword(null); 
        return ApiResponse.ok("登录成功", user);
    }


    /**
     * 修改个人资料（真实姓名、密码）- 带旧密码安全校验
     */
    @PostMapping("/update-profile")
    public ApiResponse<SysUser> updateProfile(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        String token = request.getHeader("X-User-Token");
        SysUser user = sysUserRepository.findByToken(token);

        if (user == null) {
            return ApiResponse.fail("用户不存在或登录已失效");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("password");
        String newRealName = body.get("realName");

        boolean updated = false;

        // 🔴 1. 如果传了新密码，必须先校验旧密码
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (oldPassword == null || !oldPassword.trim().equals(user.getPassword())) {
                return ApiResponse.fail("修改失败：旧密码不正确！");
            }
            user.setPassword(newPassword.trim());
            updated = true;
        }

        // 2. 如果传了真实姓名，直接更新
        if (newRealName != null && !newRealName.trim().isEmpty()) {
            user.setRealName(newRealName.trim());
            updated = true;
        }

        // 3. 只有发生变更才去存数据库
        if (updated) {
            sysUserRepository.save(user);
        }

        // 安全起见，擦除返回给前端的密码
        user.setPassword(null);
        return ApiResponse.ok("修改成功", user);
    }
}