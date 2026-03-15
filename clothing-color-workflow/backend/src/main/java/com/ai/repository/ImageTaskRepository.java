package com.ai.repository;

import com.ai.entity.ImageTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageTaskRepository extends JpaRepository<ImageTask, Long> {
    // 🔴 捞出所有正在处理中的任务
    List<ImageTask> findByStatusIn(List<String> statuses);
}
