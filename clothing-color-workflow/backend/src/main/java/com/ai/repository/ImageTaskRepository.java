package com.ai.repository;

import com.ai.entity.ImageTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ImageTaskRepository extends JpaRepository<ImageTask, Long>, JpaSpecificationExecutor<ImageTask> {

}
