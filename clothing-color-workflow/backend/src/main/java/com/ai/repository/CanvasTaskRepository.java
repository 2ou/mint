package com.ai.repository;

import com.ai.entity.CanvasTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CanvasTaskRepository extends JpaRepository<CanvasTask, Long> {
    Optional<CanvasTask> findByTaskId(String taskId);

    List<CanvasTask> findTop100ByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);

    List<CanvasTask> findTop100ByShopNameAndOperatorAndCanvasIdOrderByUpdatedAtDesc(String shopName, String operator, String canvasId);

    List<CanvasTask> findByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);

    List<CanvasTask> findTop20ByStatusIgnoreCaseOrderByUpdatedAtAsc(String status);

    long countByShopNameAndOperatorAndStatusIgnoreCase(String shopName, String operator, String status);
}
