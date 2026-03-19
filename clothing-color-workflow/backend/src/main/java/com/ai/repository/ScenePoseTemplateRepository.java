package com.ai.repository;

import com.ai.entity.ScenePoseTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScenePoseTemplateRepository extends JpaRepository<ScenePoseTemplate, Long> {

    // 🔴 新增：带分类和关键字（标题或提示词）的分页搜索
    @Query("SELECT s FROM ScenePoseTemplate s WHERE " +
            "(:category IS NULL OR :category = '' OR s.category = :category) AND " +
            "(:keyword IS NULL OR :keyword = '' OR s.title LIKE CONCAT('%', :keyword, '%') OR s.basePrompt LIKE CONCAT('%', :keyword, '%'))")
    Page<ScenePoseTemplate> searchTemplates(@Param("category") String category,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}