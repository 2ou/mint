package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.entity.SysUser;
import com.ai.repository.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
public class AdminUserController {

    private final SysUserRepository sysUserRepository;

    // 🔴 核心防御：严格校验当前 Token 对应的用户是不是 PINKSIR
    private boolean isNotAdmin(HttpServletRequest request) {
        String token = request.getHeader("X-User-Token");
        if (token == null || token.trim().isEmpty()) return true;
        SysUser user = sysUserRepository.findByToken(token);
        return user == null || !"PINKSIR".equalsIgnoreCase(user.getUsername());
    }

    // 1. 查询所有用户
    @GetMapping("/list")
    public ApiResponse<List<SysUser>> listUsers(HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("🔴 非法越权：只有超级管理员 PINKSIR 可以访问！");

        List<SysUser> users = sysUserRepository.findAll();
        // 安全起见，把真实密码擦除，用星号代替返回给前端
        users.forEach(u -> u.setPassword("********"));
        return ApiResponse.ok("ok", users);
    }

    // 2. 创建新用户
    @PostMapping("/create")
    public ApiResponse<SysUser> createUser(@RequestBody SysUser newUser, HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("无权限");
        if (sysUserRepository.findByUsername(newUser.getUsername()) != null) {
            return ApiResponse.fail("用户名已存在，请换一个！");
        }
        sysUserRepository.save(newUser);
        return ApiResponse.ok("创建成功", newUser);
    }

    // 3. 修改用户资料
    // ⚠️ 规范警告：必须加上 ("id")，防止 Spring Boot 3.2+ 版本参数名丢失报错
    @PutMapping("/update/{id}")
    public ApiResponse<SysUser> updateUser(@PathVariable("id") Long id, @RequestBody SysUser updatedData, HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("无权限");

        SysUser existing = sysUserRepository.findById(id).orElse(null);
        if (existing == null) return ApiResponse.fail("用户不存在");

        // 如果前端传了新密码，且不是 8 个星号，说明需要改密码
        if (updatedData.getPassword() != null && !updatedData.getPassword().trim().isEmpty() && !"********".equals(updatedData.getPassword())) {
            existing.setPassword(updatedData.getPassword().trim());
        }

        if (updatedData.getRealName() != null) existing.setRealName(updatedData.getRealName().trim());
        if (updatedData.getShopName() != null) existing.setShopName(updatedData.getShopName().trim());

        sysUserRepository.save(existing);
        return ApiResponse.ok("更新成功", existing);
    }

    // 4. 删除用户
    // ⚠️ 规范警告：必须加上 ("id")，防止 Spring Boot 3.2+ 版本参数名丢失报错
    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteUser(@PathVariable("id") Long id, HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("无权限");

        SysUser existing = sysUserRepository.findById(id).orElse(null);
        if (existing == null) return ApiResponse.fail("用户不存在");

        if ("PINKSIR".equalsIgnoreCase(existing.getUsername())) {
            return ApiResponse.fail("⚠️ 警告：无法删除超级管理员账号自己！");
        }

        sysUserRepository.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }
}