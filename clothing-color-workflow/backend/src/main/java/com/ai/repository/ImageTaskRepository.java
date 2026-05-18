package com.ai.repository;

import com.ai.entity.ImageTask;
import com.ai.dto.SpuStatDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ImageTaskRepository extends JpaRepository<ImageTask, Long>, JpaSpecificationExecutor<ImageTask> {

    // 🔴 极简版：只统计 spu 和 对应的总 cost
    @Query("SELECT new com.ai.dto.SpuStatDTO(" +
            "t.spu, " +
            "SUM(t.cost)) " +
            "FROM ImageTask t " +
            "WHERE t.status = 'SUCCESS' " + // 只统计成功扣费的任务
            "AND (:spu IS NULL OR t.spu LIKE CONCAT('%', :spu, '%')) " +
            "AND (:startTime IS NULL OR t.createdAt >= :startTime) " +
            "AND (:endTime IS NULL OR t.createdAt <= :endTime) " +
            "GROUP BY t.spu " +
            "ORDER BY SUM(t.cost) DESC") // 按消费金额从高到低排序
    List<SpuStatDTO> getTaskStats(@Param("spu") String spu,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

}