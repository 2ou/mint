package com.ai.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "prompt_global_config")
public class PromptGlobalConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false, unique = true)
    private String configKey;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String configValue;

    @Column(length = 100)
    private String description;
}