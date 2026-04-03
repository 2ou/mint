package com.ai.creative.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_template")
public class CreativeTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    private String templateType;
    private String category;
    private Long sourceProjectId;
    private String status;
    private Integer isSystem;
    private String coverUrl;
    private String description;
    private String canvasJson;
    private String flowJson;
    private String configJson;
    private Integer useCount;
    private Integer deleted;
    private String createBy;
    private String updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
