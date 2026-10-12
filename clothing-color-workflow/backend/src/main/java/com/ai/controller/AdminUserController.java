package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.entity.SysUser;
import com.ai.repository.SysUserRepository;
import com.ai.repository.UserSessionRepository;
import com.ai.service.AuthenticationService;
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
    private final UserSessionRepository userSessionRepository;
    private final AuthenticationService authenticationService;

    // 🔴 核心防御：严格校验当前 Token 对应的用户是不是 PINKSIR
    private boolean isNotAdmin(HttpServletRequest request) {
        Object shopName = request.getAttribute("shopName");
        return shopName == null || !"PINKSIR".equalsIgnoreCase(String.valueOf(shopName).trim());
    }

    // 1. 查询所有用户
    @GetMapping("/list")
    public ApiResponse<List<SysUser>> listUsers(HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("🔴 非法越权：只有超级管理员 PINKSIR 可以访问！");

        List<SysUser> users = sysUserRepository.findAll();
        return ApiResponse.ok("ok", users);
    }

    // 2. 创建新用户
    @PostMapping("/create")
    public ApiResponse<SysUser> createUser(@RequestBody SysUser newUser, HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("无权限");
        if (newUser.getUsername() == null || newUser.getUsername().isBlank()) {
            return ApiResponse.fail("用户名不能为空");
        }
        String username = newUser.getUsername().trim();
        if (sysUserRepository.findByUsername(username) != null) {
            return ApiResponse.fail("用户名已存在，请换一个！");
        }
        if (!authenticationService.isPasswordStrongEnough(newUser.getPassword())) {
            return ApiResponse.fail("密码至少需要8位");
        }
        newUser.setUsername(username);
        newUser.setPassword(authenticationService.encodePassword(newUser.getPassword().trim()));
        authenticationService.resetLock(newUser);
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
            if (!authenticationService.isPasswordStrongEnough(updatedData.getPassword())) {
                return ApiResponse.fail("密码至少需要8位");
            }
            existing.setPassword(authenticationService.encodePassword(updatedData.getPassword().trim()));
            authenticationService.resetLock(existing);
            userSessionRepository.deleteByUser_Id(existing.getId());
        }

        if (updatedData.getRealName() != null) existing.setRealName(updatedData.getRealName().trim());
        if (updatedData.getShopName() != null) existing.setShopName(updatedData.getShopName().trim());

        sysUserRepository.save(existing);
        return ApiResponse.ok("更新成功", existing);
    }

    @PostMapping("/{id}/unlock")
    public ApiResponse<SysUser> unlockUser(@PathVariable("id") Long id, HttpServletRequest request) {
        if (isNotAdmin(request)) return ApiResponse.fail("无权限");
        SysUser existing = sysUserRepository.findById(id).orElse(null);
        if (existing == null) return ApiResponse.fail("用户不存在");
        authenticationService.resetLock(existing);
        userSessionRepository.deleteByUser_Id(id);
        sysUserRepository.save(existing);
        return ApiResponse.ok("账号已解锁，请用户重新登录", existing);
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

        userSessionRepository.deleteByUser_Id(id);
        sysUserRepository.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }
}
