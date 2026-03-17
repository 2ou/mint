package com.ai.repository;

import com.ai.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    SysUser findByUsernameAndPassword(String username, String password);
    SysUser findByToken(String token);
}