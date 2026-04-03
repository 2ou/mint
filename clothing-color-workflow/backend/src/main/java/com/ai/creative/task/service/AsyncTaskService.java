package com.ai.creative.task.service;

import com.ai.creative.common.PageResult;
import com.ai.creative.task.dto.req.AsyncTaskPageReq;
import com.ai.creative.task.dto.resp.AsyncTaskDetailResp;
import com.ai.creative.task.dto.resp.AsyncTaskLogResp;
import com.ai.creative.task.dto.resp.AsyncTaskPageResp;

import java.util.List;

public interface AsyncTaskService {
    PageResult<AsyncTaskPageResp> page(AsyncTaskPageReq req);
    AsyncTaskDetailResp detail(Long taskId);
    void refreshTask(Long taskId);
    void retryTask(Long taskId);
    List<AsyncTaskLogResp> logs(Long taskId);
    void queryAndFinalizeTask(Long taskId);
}
