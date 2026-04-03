package com.ai.creative.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_project_log")
public class CreativeProjectLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String actionType;
    private String content;
    private String operator;
    private LocalDateTime createTime;
}
