package com.ai.repository;

import com.ai.entity.CanvasTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CanvasTaskRepository extends JpaRepository<CanvasTask, Long>, JpaSpecificationExecutor<CanvasTask> {
    Optional<CanvasTask> findByTaskId(String taskId);

    List<CanvasTask> findTop100ByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);

    List<CanvasTask> findTop100ByShopNameAndOperatorAndCanvasIdOrderByUpdatedAtDesc(String shopName, String operator, String canvasId);

    List<CanvasTask> findByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);

    List<CanvasTask> findTop20ByStatusIgnoreCaseOrderByUpdatedAtAsc(String status);

    long countByShopNameAndOperatorAndStatusIgnoreCase(String shopName, String operator, String status);

    /** Platform-wide shop list for the admin console filter (跨店铺，不按当前登录店铺收敛). */
    @Query("select distinct t.shopName from CanvasTask t "
            + "where t.shopName is not null and t.shopName <> '' order by t.shopName asc")
    List<String> findDistinctShopNames();
}
