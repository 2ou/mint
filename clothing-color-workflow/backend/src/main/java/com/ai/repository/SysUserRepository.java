package com.ai.repository;

import com.ai.entity.SysUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    // 🔴 新增：根据用户名查找用户（用于管理员面板判断账号是否重复）
    SysUser findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SysUser u where u.username = :username")
    Optional<SysUser> findByUsernameForUpdate(@Param("username") String username);
}
