package com.ai.repository;

import com.ai.entity.ModelLibrary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelLibraryRepository extends JpaRepository<ModelLibrary, Long> {

    /**
     * 分页搜索模特库
     */
    @Query("SELECT m FROM ModelLibrary m WHERE " +
           "(:type IS NULL OR :type = '' OR m.modelType = :type) AND " +
           "(:keyword IS NULL OR :keyword = '' OR m.modelName LIKE CONCAT('%', :keyword, '%') OR m.styleTags LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:status IS NULL OR :status = '' OR m.status = :status)")
    Page<ModelLibrary> searchModels(@Param("type") String type,
                                    @Param("keyword") String keyword,
                                    @Param("status") String status,
                                    Pageable pageable);

    /**
     * 根据状态和类型查找模特
     */
    List<ModelLibrary> findByStatusAndModelType(String status, String modelType);

    /**
     * 获取所有已激活的模特类型
     */
    @Query("SELECT DISTINCT m.modelType FROM ModelLibrary m WHERE m.status = 'ACTIVE'")
    List<String> findActiveModelTypes();

    /**
     * 根据任务 ID 查找模特
     */
    ModelLibrary findByTaskId(String taskId);

    /**
     * 查找所有处理中的任务
     */
    @Query("SELECT m FROM ModelLibrary m WHERE m.taskStatus = 'CREATED' OR m.taskStatus = 'PROCESSING'")
    List<ModelLibrary> findProcessingTasks();

    /**
     * 统计各类型模特数量
     */
    @Query("SELECT m.modelType, COUNT(m) FROM ModelLibrary m WHERE m.status = 'ACTIVE' GROUP BY m.modelType")
    List<Object[]> countByModelType();
}
