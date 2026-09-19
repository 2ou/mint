package com.ai.repository;

import com.ai.entity.TemplateLabPersonalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateLabPersonalTemplateRepository extends JpaRepository<TemplateLabPersonalTemplate, Long> {
    List<TemplateLabPersonalTemplate> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    Optional<TemplateLabPersonalTemplate> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
