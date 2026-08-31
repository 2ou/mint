package com.ai.repository;

import com.ai.entity.ModelPriceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelPriceRuleRepository extends JpaRepository<ModelPriceRule, Long> {
    List<ModelPriceRule> findByVersion_IdOrderByPriorityDescIdAsc(Long versionId);
    List<ModelPriceRule> findByVersion_IdAndActiveTrueOrderByPriorityDescIdAsc(Long versionId);
    void deleteByVersion_Id(Long versionId);
}
