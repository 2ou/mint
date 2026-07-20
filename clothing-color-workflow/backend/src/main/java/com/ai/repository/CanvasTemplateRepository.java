package com.ai.repository;

import com.ai.entity.CanvasTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CanvasTemplateRepository extends JpaRepository<CanvasTemplate, Long> {
    List<CanvasTemplate> findByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);
}
