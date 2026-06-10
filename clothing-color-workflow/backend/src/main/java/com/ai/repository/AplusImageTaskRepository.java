package com.ai.repository;

import com.ai.entity.AplusImageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AplusImageTaskRepository extends JpaRepository<AplusImageTask, Long> {
    List<AplusImageTask> findByProjectId(Long projectId);
    List<AplusImageTask> findByStatus(String status);
    List<AplusImageTask> findByProjectIdAndModuleCode(Long projectId, String moduleCode);
}
