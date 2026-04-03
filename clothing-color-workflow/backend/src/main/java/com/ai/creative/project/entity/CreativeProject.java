package com.ai.creative.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_project")
public class CreativeProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectCode;
    private String projectName;
    private String projectType;
    private String status;
    private Long sourceTemplateId;
    private Integer currentVersionNo;
    private String currentCanvasJson;
    private String currentFlowJson;
    private String currentConfigJson;
    private String coverUrl;
    private String description;
    private LocalDateTime lastRunTime;
    private LocalDateTime lastSaveTime;
    private String remark;
    private Integer deleted;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
