package com.ai.repository;

import com.ai.entity.TemplateLabSystemTemplateDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateLabSystemTemplateDeletionRepository
        extends JpaRepository<TemplateLabSystemTemplateDeletion, Long> {

    boolean existsByTemplateId(String templateId);
}
