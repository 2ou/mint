package com.ai.repository;

import com.ai.entity.TemplateLabProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateLabProjectRepository extends JpaRepository<TemplateLabProject, Long> {
    List<TemplateLabProject> findByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);

    Optional<TemplateLabProject> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
