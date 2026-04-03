package com.ai.creative.task.service.impl;

import com.ai.creative.asset.entity.CreativeAsset;
import com.ai.creative.asset.mapper.CreativeAssetMapper;
import com.ai.creative.common.CodeGenUtils;
import com.ai.creative.common.CreativeAsserts;
import com.ai.creative.common.PageResult;
import com.ai.creative.enums.AsyncTaskStatusEnum;
import com.ai.creative.enums.AssetSourceTypeEnum;
import com.ai.creative.enums.NodeRunStatusEnum;
import com.ai.creative.oss.CreativeOssService;
import com.ai.creative.provider.kie.client.KieApiClient;
import com.ai.creative.provider.kie.parser.KieResultParser;
import com.ai.creative.run.entity.CreativeNodeRun;
import com.ai.creative.run.mapper.CreativeNodeRunMapper;
import com.ai.creative.task.convert.AsyncTaskConvert;
import com.ai.creative.task.dto.req.AsyncTaskPageReq;
import com.ai.creative.task.dto.resp.AsyncTaskDetailResp;
import com.ai.creative.task.dto.resp.AsyncTaskLogResp;
import com.ai.creative.task.dto.resp.AsyncTaskPageResp;
import com.ai.creative.task.entity.CreativeAsyncTask;
import com.ai.creative.task.entity.CreativeAsyncTaskLog;
import com.ai.creative.task.mapper.CreativeAsyncTaskLogMapper;
import com.ai.creative.task.mapper.CreativeAsyncTaskMapper;
import com.ai.creative.task.service.AsyncTaskService;
import com.ai.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {
    private final CreativeAsyncTaskMapper taskMapper;
    private final CreativeAsyncTaskLogMapper taskLogMapper;
    private final CreativeNodeRunMapper nodeRunMapper;
    private final CreativeAssetMapper assetMapper;
    private final KieApiClient kieApiClient;
    private final KieResultParser kieResultParser;
    private final CreativeOssService creativeOssService;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    public PageResult<AsyncTaskPageResp> page(AsyncTaskPageReq req) {
        Page<CreativeAsyncTask> page = taskMapper.selectPage(new Page<>(req.getPageNo(), req.getPageSize()),
                new LambdaQueryWrapper<CreativeAsyncTask>()
                        .eq(CreativeAsyncTask::getDeleted, 0)
                        .eq(req.getProjectId() != null, CreativeAsyncTask::getProjectId, req.getProjectId())
                        .eq(req.getStatus() != null, CreativeAsyncTask::getStatus, req.getStatus())
                        .eq(req.getTaskType() != null, CreativeAsyncTask::getTaskType, req.getTaskType())
                        .orderByDesc(CreativeAsyncTask::getUpdateTime));
        return PageResult.of(req.getPageNo(), req.getPageSize(), page.getTotal(), page.getRecords().stream().map(AsyncTaskConvert::toPage).toList());
    }

    @Override
    public AsyncTaskDetailResp detail(Long taskId) {
        CreativeAsyncTask task = taskMapper.selectById(taskId);
        CreativeAsserts.notNull(task, "task not found");
        return AsyncTaskConvert.toDetail(task);
    }

    @Override
    public void refreshTask(Long taskId) {
        queryAndFinalizeTask(taskId);
    }

    @Override
    @Transactional
    public void retryTask(Long taskId) {
        CreativeAsyncTask task = taskMapper.selectById(taskId);
        CreativeAsserts.notNull(task, "task not found");
        task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        task.setNextRetryTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        insertLog(taskId, "RETRY", "retry query/finalize only, no recreate");
        queryAndFinalizeTask(taskId);
    }

    @Override
    public List<AsyncTaskLogResp> logs(Long taskId) {
        return taskLogMapper.selectList(new LambdaQueryWrapper<CreativeAsyncTaskLog>()
                        .eq(CreativeAsyncTaskLog::getTaskId, taskId)
                        .orderByDesc(CreativeAsyncTaskLog::getCreateTime))
                .stream().map(AsyncTaskConvert::toLog).toList();
    }

    @Override
    @Transactional
    public void queryAndFinalizeTask(Long taskId) {
        CreativeAsyncTask task = taskMapper.selectById(taskId);
        CreativeAsserts.notNull(task, "task not found");
        if (task.getProviderTaskId() == null || task.getProviderTaskId().isBlank()) {
            throw new BusinessException("provider_task_id is empty");
        }

        JsonNode detail = kieApiClient.queryTaskDetail(task.getProviderTaskId());
        String providerStatus = kieResultParser.parseStatus(detail);
        task.setProviderResponseJson(detail.toString());
        task.setLastQueryTime(LocalDateTime.now());
        task.setNextRetryTime(LocalDateTime.now().plusMinutes(3));

        if ("SUCCESS".equalsIgnoreCase(providerStatus)) {
            handleSuccess(task, detail);
        } else if ("FAIL".equalsIgnoreCase(providerStatus) || "FAILED".equalsIgnoreCase(providerStatus)) {
            handleFail(task, providerStatus, detail.path("message").asText("provider fail"));
        } else if ("QUEUING".equalsIgnoreCase(providerStatus)) {
            task.setStatus(AsyncTaskStatusEnum.QUEUING.name());
        } else if ("PROCESSING".equalsIgnoreCase(providerStatus) || "RUNNING".equalsIgnoreCase(providerStatus)) {
            task.setStatus(AsyncTaskStatusEnum.PROCESSING.name());
        } else {
            task.setStatus(AsyncTaskStatusEnum.SUBMITTED.name());
        }

        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        insertLog(taskId, "QUERY", "providerStatus=" + providerStatus + ", taskStatus=" + task.getStatus());
    }

    private void handleSuccess(CreativeAsyncTask task, JsonNode detail) {
        String resultUrl = kieResultParser.parseResultUrl(detail);
        if (resultUrl == null || resultUrl.isBlank()) {
            handleFail(task, "RESULT_URL_EMPTY", "provider success but result_url missing");
            return;
        }

        task.setStatus(AsyncTaskStatusEnum.SUCCESS.name());
        task.setResultUrl(resultUrl);
        task.setFinishTime(LocalDateTime.now());

        if (task.getFinalAssetId() == null) {
            Long assetId = downloadAndSaveAsset(task, resultUrl);
            if (assetId != null) {
                task.setFinalAssetId(assetId);
            }
        }

        CreativeNodeRun run = nodeRunMapper.selectById(task.getNodeRunId());
        if (run != null) {
            run.setOutputJson("{\"taskId\":" + task.getId() + ",\"assetId\":" + task.getFinalAssetId() + ",\"url\":\"" + resultUrl + "\"}");
            run.setStatus(NodeRunStatusEnum.SUCCESS.name());
            run.setEndTime(LocalDateTime.now());
            run.setUpdateTime(LocalDateTime.now());
            nodeRunMapper.updateById(run);
        }
    }

    private void handleFail(CreativeAsyncTask task, String failCode, String failMsg) {
        task.setStatus(AsyncTaskStatusEnum.FAIL.name());
        task.setFailCode(failCode);
        task.setFailMsg(failMsg);
        task.setFinishTime(LocalDateTime.now());

        CreativeNodeRun run = nodeRunMapper.selectById(task.getNodeRunId());
        if (run != null) {
            run.setStatus(NodeRunStatusEnum.FAIL.name());
            run.setErrorCode(failCode);
            run.setErrorMsg(failMsg);
            run.setEndTime(LocalDateTime.now());
            run.setUpdateTime(LocalDateTime.now());
            nodeRunMapper.updateById(run);
        }
    }

    private Long downloadAndSaveAsset(CreativeAsyncTask task, String resultUrl) {
        try {
            Request request = new Request.Builder().url(resultUrl).get().build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    insertLog(task.getId(), "FINALIZE_FAIL", "download failed http=" + response.code());
                    return null;
                }
                byte[] bytes = response.body().bytes();
                String key = creativeOssService.generateObjectKey("video", task.getTaskCode() + ".mp4");
                String ossUrl = creativeOssService.uploadBytes(key, bytes);

                CreativeAsset asset = new CreativeAsset();
                asset.setAssetCode(CodeGenUtils.code("AST"));
                asset.setProjectId(task.getProjectId());
                asset.setProjectVersionId(task.getProjectVersionId());
                asset.setSourceType(AssetSourceTypeEnum.GENERATED.name());
                asset.setAssetType("VIDEO");
                asset.setBizType("OUTPUT");
                asset.setFileName(task.getTaskCode() + ".mp4");
                asset.setFileExt("mp4");
                asset.setMimeType("video/mp4");
                asset.setFileSize((long) bytes.length);
                asset.setOssUrl(ossUrl);
                asset.setSourceUrl(resultUrl);
                asset.setDeleted(0);
                asset.setCreateTime(LocalDateTime.now());
                assetMapper.insert(asset);
                insertLog(task.getId(), "FINALIZE", "create asset=" + asset.getId());
                return asset.getId();
            }
        } catch (Exception e) {
            log.error("download and save asset fail", e);
            insertLog(task.getId(), "FINALIZE_FAIL", e.getMessage());
            return null;
        }
    }

    private void insertLog(Long taskId, String type, String content) {
        CreativeAsyncTaskLog item = new CreativeAsyncTaskLog();
        item.setTaskId(taskId);
        item.setLogType(type);
        item.setContent(content);
        item.setCreateTime(LocalDateTime.now());
        taskLogMapper.insert(item);
    }
}
