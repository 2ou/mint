package com.ai.creative.task.convert;

import com.ai.creative.task.dto.resp.AsyncTaskDetailResp;
import com.ai.creative.task.dto.resp.AsyncTaskLogResp;
import com.ai.creative.task.dto.resp.AsyncTaskPageResp;
import com.ai.creative.task.entity.CreativeAsyncTask;
import com.ai.creative.task.entity.CreativeAsyncTaskLog;
import org.springframework.beans.BeanUtils;

public class AsyncTaskConvert {
    public static AsyncTaskPageResp toPage(CreativeAsyncTask e){ AsyncTaskPageResp r = new AsyncTaskPageResp(); BeanUtils.copyProperties(e,r); return r; }
    public static AsyncTaskDetailResp toDetail(CreativeAsyncTask e){ AsyncTaskDetailResp r = new AsyncTaskDetailResp(); BeanUtils.copyProperties(e,r); return r; }
    public static AsyncTaskLogResp toLog(CreativeAsyncTaskLog e){ AsyncTaskLogResp r = new AsyncTaskLogResp(); BeanUtils.copyProperties(e,r); return r; }
}
