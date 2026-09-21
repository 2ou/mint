package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.dto.KieTaskResult;
import com.ai.entity.CanvasTask;
import com.ai.repository.CanvasTaskRepository;
import com.aliyun.oss.OSS;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanvasTaskServiceTest {

    @Test
    void deletesRecordedTemplateLabInputWhenProviderFinishes() {
        CanvasTaskRepository repository = mock(CanvasTaskRepository.class);
        OssService ossService = mock(OssService.class);
        OSS oss = mock(OSS.class);
        when(ossService.getOssClient()).thenReturn(oss);

        AppProperties properties = new AppProperties();
        properties.getOss().setResultBucket("result-bucket");
        CanvasTaskService service = new CanvasTaskService(
                repository,
                new ObjectMapper(),
                ossService,
                properties,
                mock(Environment.class),
                mock(ModelPricingService.class));

        CanvasTask task = new CanvasTask();
        task.setTaskId("cutout-task-1");
        task.setStatus("PROCESSING");
        task.setRequestPayloadJson("{\"model\":\"recraft/remove-background\","
                + "\"provider_input_url\":\"https://example.com/input.jpg\","
                + "\"provider_input_object\":\"TEMPLATE_LAB/PINKSIR/7/81/cutout-input/input.jpg\"}");
        when(repository.findByTaskId("cutout-task-1")).thenReturn(Optional.of(task));

        service.recordPolledResult(KieTaskResult.builder()
                .taskId("cutout-task-1")
                .status("FAILED")
                .finished(true)
                .success(false)
                .errorMessage("provider rejected input")
                .build());

        verify(oss).deleteObject(
                "result-bucket", "TEMPLATE_LAB/PINKSIR/7/81/cutout-input/input.jpg");
        verify(repository).save(task);
        assertFalse(task.getRequestPayloadJson().contains("provider_input_object"));
        assertFalse(task.getRequestPayloadJson().contains("provider_input_url"));
    }
}
