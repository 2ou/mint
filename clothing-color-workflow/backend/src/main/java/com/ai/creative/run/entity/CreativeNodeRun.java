package com.ai.creative.run.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("creative_node_run")
public class CreativeNodeRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runCode;
    private Long projectId;
    private Long projectVersionId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String provider;
    private String modelCode;
    private String runMode;
    private String status;
    private String inputJson;
    private String requestJson;
    private String outputJson;
    private Long selectedOutputAssetId;
    private String errorCode;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer deleted;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
