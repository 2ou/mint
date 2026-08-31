package com.ai.repository;

import com.ai.entity.ModelPriceVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelPriceVersionRepository extends JpaRepository<ModelPriceVersion, Long> {
    Optional<ModelPriceVersion> findFirstByStatusOrderByPublishedAtDesc(String status);
    boolean existsByStatus(String status);

    Optional<ModelPriceVersion> findByVersionCode(String versionCode);
}
