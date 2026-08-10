package com.ai.repository;

import com.ai.entity.ModelIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelIdentityRepository extends JpaRepository<ModelIdentity, Long> {
    List<ModelIdentity> findByStatusOrderByUpdatedAtDesc(String status);
}
