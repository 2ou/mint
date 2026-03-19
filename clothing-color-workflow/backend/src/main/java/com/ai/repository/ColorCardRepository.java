package com.ai.repository;

import com.ai.entity.ColorCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ColorCardRepository extends JpaRepository<ColorCard, Long> {

    // 🔴 1. 用于【新增】校验：库里是否已经存在相同的 面料 + 花型
    boolean existsByFabricTypeAndPatternName(String fabricType, String patternName);

    // 🔴 2. 用于【编辑】校验：库里是否已经存在相同的 面料 + 花型，且 ID 不是当前这条数据
    boolean existsByFabricTypeAndPatternNameAndIdNot(String fabricType, String patternName, Long id);

    // 🔴 新增：带搜索条件的分页查询
    @Query("SELECT c FROM ColorCard c WHERE " +
            "(:fabricType IS NULL OR :fabricType = '' OR c.fabricType LIKE CONCAT('%', :fabricType, '%')) AND " +
            "(:printType IS NULL OR c.printType = :printType) AND " +
            "(:patternName IS NULL OR :patternName = '' OR c.patternName LIKE CONCAT('%', :patternName, '%'))")
    Page<ColorCard> searchColorCards(@Param("fabricType") String fabricType,
                                     @Param("printType") Integer printType,
                                     @Param("patternName") String patternName,
                                     Pageable pageable);

}