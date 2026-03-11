package com.ai.repository;

import com.ai.entity.ImageTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageTaskRepository extends JpaRepository<ImageTask, Long> {
}
