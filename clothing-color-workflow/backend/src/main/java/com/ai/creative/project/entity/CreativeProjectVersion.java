package com.ai.creative.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_project_version")
public class CreativeProjectVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer versionNo;
    private String saveType;
    private String canvasJson;
    private String flowJson;
    private String configJson;
    private String summary;
    private Integer isCurrent;
    private Integer deleted;
    private String createBy;
    private LocalDateTime createTime;
}
