package com.ai.repository;

import com.ai.entity.ImageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ImageTaskRepository extends JpaRepository<ImageTask, Long>, JpaSpecificationExecutor<ImageTask> {
    // 🔴 捞出所有正在处理中的任务
    List<ImageTask> findByStatusIn(List<String> statuses);

    // 专门用于定时任务兜底：查询状态为 SUCCESS，且有临时链接但没存进 OSS 的前 5 条记录
    List<ImageTask> findTop5ByStatusAndResultTempUrlIsNotNullAndResultOssUrlIsNullOrderByIdDesc(String status);
}
