package com.ai.creative.run.service.impl;

import com.ai.creative.common.CodeGenUtils;
import com.ai.creative.common.CreativeAsserts;
import com.ai.creative.config.CreativeProperties;
import com.ai.creative.enums.*;
import com.ai.creative.project.entity.CreativeProject;
import com.ai.creative.project.entity.CreativeProjectLog;
import com.ai.creative.project.mapper.CreativeProjectLogMapper;
import com.ai.creative.project.mapper.CreativeProjectMapper;
import com.ai.creative.provider.kie.builder.KiePayloadBuilder;
import com.ai.creative.provider.kie.client.KieApiClient;
import com.ai.creative.run.convert.NodeRunConvert;
import com.ai.creative.run.dto.req.NodeRunContinueReq;
import com.ai.creative.run.dto.req.NodeRunReq;
import com.ai.creative.run.dto.req.NodeRunSelectOutputReq;
import com.ai.creative.run.dto.resp.NodeRunDetailResp;
import com.ai.creative.run.dto.resp.NodeRunPageResp;
import com.ai.creative.run.entity.CreativeNodeRun;
import com.ai.creative.run.mapper.CreativeNodeRunMapper;
import com.ai.creative.run.service.NodeRunService;
import com.ai.creative.task.entity.CreativeAsyncTask;
import com.ai.creative.task.entity.CreativeAsyncTaskLog;
import com.ai.creative.task.mapper.CreativeAsyncTaskLogMapper;
import com.ai.creative.task.mapper.CreativeAsyncTaskMapper;
import com.ai.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRunServiceImpl implements NodeRunService {
    private final CreativeNodeRunMapper nodeRunMapper;
    private final CreativeProjectMapper projectMapper;
    private final CreativeProjectLogMapper projectLogMapper;
    private final CreativeAsyncTaskMapper asyncTaskMapper;
    private final CreativeAsyncTaskLogMapper asyncTaskLogMapper;
    private final KiePayloadBuilder kiePayloadBuilder;
    private final KieApiClient kieApiClient;
    private final CreativeProperties creativeProperties;

    @Override
    @Transactional
    public NodeRunDetailResp runNode(NodeRunReq req) {
        CreativeProject project = projectMapper.selectById(req.getProjectId());
        CreativeAsserts.notNull(project, "project not found");

        CreativeNodeRun run = new CreativeNodeRun();
        run.setRunCode(CodeGenUtils.code("RUN"));
        run.setProjectId(req.getProjectId());
        run.setProjectVersionId(req.getProjectVersionId());
        run.setNodeId(req.getNodeId());
        run.setNodeName(req.getNodeName());
        run.setNodeType(req.getNodeType());
        run.setProvider(req.getProvider());
        run.setModelCode(req.getModelCode());
        run.setRunMode(req.getRunMode());
        run.setStatus(NodeRunStatusEnum.RUNNING.name());
        run.setInputJson(req.getInputJson());
        run.setRequestJson(req.getRequestJson());
        run.setDeleted(0);
        run.setStartTime(LocalDateTime.now());
        run.setCreateTime(LocalDateTime.now());
        run.setUpdateTime(LocalDateTime.now());
        nodeRunMapper.insert(run);

        Long asyncTaskId = null;
        NodeTypeEnum nodeType = NodeTypeEnum.valueOf(req.getNodeType());
        if (nodeType == NodeTypeEnum.IMAGE_TO_VIDEO || nodeType == NodeTypeEnum.VIDEO_TO_VIDEO) {
            asyncTaskId = createAsyncTask(run, req);
        } else {
            handleSyncNode(run, req);
        }

        project.setLastRunTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        projectMapper.updateById(project);

        CreativeProjectLog projectLog = new CreativeProjectLog();
        projectLog.setProjectId(req.getProjectId());
        projectLog.setActionType(ProjectLogActionEnum.RUN.name());
        projectLog.setContent("run node:" + req.getNodeId() + ", type=" + req.getNodeType());
        projectLog.setOperator("system");
        projectLog.setCreateTime(LocalDateTime.now());
        projectLogMapper.insert(projectLog);

        NodeRunDetailResp resp = NodeRunConvert.toDetail(nodeRunMapper.selectById(run.getId()));
        resp.setAsyncTaskId(asyncTaskId);
        return resp;
    }

    private void handleSyncNode(CreativeNodeRun run, NodeRunReq req) {
        run.setStatus(NodeRunStatusEnum.SUCCESS.name());
        run.setOutputJson("{\"nodeId\":\"" + req.getNodeId() + "\",\"nodeType\":\"" + req.getNodeType() + "\",\"echoInput\":" + safeJson(req.getInputJson()) + "}");
        run.setEndTime(LocalDateTime.now());
        run.setUpdateTime(LocalDateTime.now());
        nodeRunMapper.updateById(run);
    }

    private String safeJson(String json) {
        if (json == null || json.isBlank()) {
            return "{}";
        }
        return json;
    }

    private Long createAsyncTask(CreativeNodeRun run, NodeRunReq req) {
        CreativeAsyncTask task = new CreativeAsyncTask();
        task.setTaskCode(CodeGenUtils.code("TSK"));
        task.setProjectId(req.getProjectId());
        task.setProjectVersionId(req.getProjectVersionId());
        task.setNodeRunId(run.getId());
        task.setTaskType(NodeTypeEnum.IMAGE_TO_VIDEO.name().equals(req.getNodeType()) ? AsyncTaskTypeEnum.VIDEO_GEN.name() : AsyncTaskTypeEnum.VIDEO_TO_VIDEO.name());
        task.setProvider(req.getProvider() == null ? "KIE" : req.getProvider());
        task.setModelCode(req.getModelCode() == null ? creativeProperties.getKie().getKlingVideoModel() : req.getModelCode());
        task.setStatus(AsyncTaskStatusEnum.INIT.name());
        task.setRequestJson(req.getRequestJson());
        task.setRetryCount(0);
        task.setCallbackCount(0);
        task.setDeleted(0);
        task.setStartTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        asyncTaskMapper.insert(task);

        try {
            JsonNode payload = NodeTypeEnum.IMAGE_TO_VIDEO.name().equals(req.getNodeType())
                    ? kiePayloadBuilder.buildImageToVideoPayload(task.getModelCode(), req.getRequestJson())
                    : kiePayloadBuilder.buildVideoToVideoPayload(task.getModelCode(), req.getRequestJson());
            JsonNode providerResp = kieApiClient.createTask(payload);
            String providerTaskId = providerResp.path("task_id").asText(providerResp.path("id").asText(null));
            if (providerTaskId == null || providerTaskId.isBlank()) {
                throw new BusinessException("kie createTask missing provider_task_id");
            }
            task.setProviderTaskId(providerTaskId);
            task.setProviderResponseJson(providerResp.toString());
            task.setStatus(AsyncTaskStatusEnum.SUBMITTED.name());
            task.setLastQueryTime(LocalDateTime.now());
            task.setNextRetryTime(LocalDateTime.now().plusMinutes(3));
            task.setUpdateTime(LocalDateTime.now());
            asyncTaskMapper.updateById(task);
            insertTaskLog(task.getId(), "CREATE", providerResp.toString());
            return task.getId();
        } catch (Exception ex) {
            log.error("create async task failed", ex);
            task.setStatus(AsyncTaskStatusEnum.FAIL.name());
            task.setFailCode("CREATE_TASK_FAIL");
            task.setFailMsg(ex.getMessage());
            task.setFinishTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            asyncTaskMapper.updateById(task);

            run.setStatus(NodeRunStatusEnum.FAIL.name());
            run.setErrorCode("CREATE_TASK_FAIL");
            run.setErrorMsg(ex.getMessage());
            run.setEndTime(LocalDateTime.now());
            run.setUpdateTime(LocalDateTime.now());
            nodeRunMapper.updateById(run);
            insertTaskLog(task.getId(), "CREATE_FAIL", ex.getMessage());
            return task.getId();
        }
    }

    private void insertTaskLog(Long taskId, String type, String content) {
        CreativeAsyncTaskLog logItem = new CreativeAsyncTaskLog();
        logItem.setTaskId(taskId);
        logItem.setLogType(type);
        logItem.setContent(content);
        logItem.setCreateTime(LocalDateTime.now());
        asyncTaskLogMapper.insert(logItem);
    }

    @Override
    public NodeRunDetailResp continueFromNode(NodeRunContinueReq req) {
        CreativeNodeRun prev = nodeRunMapper.selectById(req.getFromRunId());
        CreativeAsserts.notNull(prev, "from run not found");
        CreativeAsserts.isTrue(prev.getProjectId().equals(req.getProjectId()), "from run project not match");
        return runNode(req.getNextNode());
    }

    @Override
    public NodeRunDetailResp detail(Long runId) {
        CreativeNodeRun run = nodeRunMapper.selectById(runId);
        CreativeAsserts.notNull(run, "run not found");
        NodeRunDetailResp resp = NodeRunConvert.toDetail(run);
        CreativeAsyncTask task = asyncTaskMapper.selectOne(new LambdaQueryWrapper<CreativeAsyncTask>()
                .eq(CreativeAsyncTask::getNodeRunId, runId)
                .eq(CreativeAsyncTask::getDeleted, 0)
                .last("limit 1"));
        if (task != null) {
            resp.setAsyncTaskId(task.getId());
        }
        return resp;
    }

    @Override
    public List<NodeRunPageResp> listByProject(Long projectId) {
        return nodeRunMapper.selectList(new LambdaQueryWrapper<CreativeNodeRun>()
                        .eq(CreativeNodeRun::getProjectId, projectId)
                        .eq(CreativeNodeRun::getDeleted, 0)
                        .orderByDesc(CreativeNodeRun::getCreateTime))
                .stream().map(NodeRunConvert::toPage).toList();
    }

    @Override
    public void selectOutput(Long runId, NodeRunSelectOutputReq req) {
        CreativeNodeRun run = nodeRunMapper.selectById(runId);
        CreativeAsserts.notNull(run, "run not found");
        run.setSelectedOutputAssetId(req.getAssetId());
        run.setUpdateTime(LocalDateTime.now());
        nodeRunMapper.updateById(run);
    }
}
