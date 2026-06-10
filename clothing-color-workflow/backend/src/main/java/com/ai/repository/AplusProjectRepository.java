package com.ai.repository;

import com.ai.entity.AplusProject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AplusProjectRepository extends JpaRepository<AplusProject, Long> {
    Page<AplusProject> findByStatusIn(List<String> statuses, Pageable pageable);
    Page<AplusProject> findBySpuContaining(String spu, Pageable pageable);
    Page<AplusProject> findBySpuContainingAndStatusIn(String spu, List<String> statuses, Pageable pageable);
    List<AplusProject> findByStatus(String status);
}
