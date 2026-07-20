package com.ai.repository;

import com.ai.entity.CanvasTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CanvasTaskRepository extends JpaRepository<CanvasTask, Long> {
    Optional<CanvasTask> findByTaskId(String taskId);
}
