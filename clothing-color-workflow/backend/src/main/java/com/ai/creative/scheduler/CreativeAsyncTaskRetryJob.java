package com.ai.creative.scheduler;

import com.ai.creative.enums.AsyncTaskStatusEnum;
import com.ai.creative.task.entity.CreativeAsyncTask;
import com.ai.creative.task.mapper.CreativeAsyncTaskMapper;
import com.ai.creative.task.service.AsyncTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreativeAsyncTaskRetryJob {
    private final CreativeAsyncTaskMapper taskMapper;
    private final AsyncTaskService asyncTaskService;

    @Scheduled(cron = "0 */3 * * * ?")
    public void retry() {
        LocalDateTime now = LocalDateTime.now();
        List<CreativeAsyncTask> list = taskMapper.selectList(new LambdaQueryWrapper<CreativeAsyncTask>()
                .eq(CreativeAsyncTask::getDeleted, 0)
                .in(CreativeAsyncTask::getStatus,
                        AsyncTaskStatusEnum.SUBMITTED.name(),
                        AsyncTaskStatusEnum.QUEUING.name(),
                        AsyncTaskStatusEnum.PROCESSING.name())
                .and(w -> w.le(CreativeAsyncTask::getNextRetryTime, now).or().isNull(CreativeAsyncTask::getNextRetryTime)));

        for (CreativeAsyncTask task : list) {
            try {
                asyncTaskService.refreshTask(task.getId());
                task.setLastQueryTime(LocalDateTime.now());
                task.setNextRetryTime(LocalDateTime.now().plusMinutes(3));
                taskMapper.updateById(task);
            } catch (Exception e) {
                log.error("refresh task fail: {}", task.getId(), e);
            }
        }
    }
}
