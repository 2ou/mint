package com.ai.repository;

import com.ai.entity.CanvasProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CanvasProjectRepository extends JpaRepository<CanvasProject, Long> {

    List<CanvasProject> findByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);

    Optional<CanvasProject> findTopByShopNameAndOperatorOrderByUpdatedAtDesc(String shopName, String operator);
}
