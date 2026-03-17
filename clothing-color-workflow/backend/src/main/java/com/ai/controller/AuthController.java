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
}