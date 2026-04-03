package com.ai.creative.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_async_task")
public class CreativeAsyncTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private Long projectId;
    private Long projectVersionId;
    private Long nodeRunId;
    private String taskType;
    private String provider;
    private String modelCode;
    private String providerTaskId;
    private String callbackUrl;
    private String status;
    private String requestJson;
    private String providerResponseJson;
    private String resultJson;
    private String resultUrl;
    private Long finalAssetId;
    private String failCode;
    private String failMsg;
    private Integer retryCount;
    private Integer callbackCount;
    private LocalDateTime lastQueryTime;
    private LocalDateTime nextRetryTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private Integer deleted;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
