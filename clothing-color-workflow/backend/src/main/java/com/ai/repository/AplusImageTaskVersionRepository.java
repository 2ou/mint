package com.ai.repository;

import com.ai.entity.AplusImageTaskVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AplusImageTaskVersionRepository extends JpaRepository<AplusImageTaskVersion, Long> {
    List<AplusImageTaskVersion> findByTaskIdOrderByVersionNumberDesc(Long taskId);
}
