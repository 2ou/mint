package com.ai.repository;

import com.ai.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    SysUser findByUsernameAndPassword(String username, String password);
    SysUser findByToken(String token);

    // 🔴 新增：根据用户名查找用户（用于管理员面板判断账号是否重复）
    SysUser findByUsername(String username);
}