package com.ai.repository;
import com.ai.entity.PromptGlobalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PromptGlobalConfigRepository extends JpaRepository<PromptGlobalConfig, Long> {
    PromptGlobalConfig findByConfigKey(String configKey);
}