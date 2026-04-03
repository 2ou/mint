package com.ai.creative.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_async_task_log")
public class CreativeAsyncTaskLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String logType;
    private String content;
    private LocalDateTime createTime;
}
