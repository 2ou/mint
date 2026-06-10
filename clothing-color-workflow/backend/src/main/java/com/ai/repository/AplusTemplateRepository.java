package com.ai.repository;

import com.ai.entity.AplusTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AplusTemplateRepository extends JpaRepository<AplusTemplate, Long> {
    Page<AplusTemplate> findByTemplateNameContaining(String templateName, Pageable pageable);
    Page<AplusTemplate> findBySpu(String spu, Pageable pageable);
    Page<AplusTemplate> findByTemplateNameContainingAndSpu(String templateName, String spu, Pageable pageable);
}
