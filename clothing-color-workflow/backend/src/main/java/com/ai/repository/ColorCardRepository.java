package com.ai.repository;

import com.ai.entity.ColorCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorCardRepository extends JpaRepository<ColorCard, Long> {
}