package com.ai.repository;

import com.ai.entity.SysUser;
import com.ai.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Query("select session.user from UserSession session where session.token = :token")
    SysUser findUserByToken(@Param("token") String token);

    @Query("select session from UserSession session join fetch session.user where session.token = :token")
    Optional<UserSession> findByTokenWithUser(@Param("token") String token);

    @Transactional
    long deleteByUser_Id(Long userId);
}
