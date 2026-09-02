package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.repository.CanvasProjectRepository;
import com.ai.service.CanvasMediaCleanupService;
import com.ai.service.CanvasTaskService;
import com.ai.service.KieClientService;
import com.ai.service.ModelPricingService;
import com.ai.service.OssService;
import com.ai.service.Seedance25VideoRequestService;
import com.ai.service.TextModelService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfiniteCanvasVideoReferenceTest {

    @TempDir
    Path localSaveRoot;

    @Test
    void uploadsLocalGeneratedReferenceBeforeSubmittingKieVideo() throws Exception {
        Path image = localSaveRoot.resolve("canvas/generated-reference.png");
        Files.createDirectories(image.getParent());
        Files.write(image, new byte[]{1, 2, 3, 4});

        OSS ossClient = mock(OSS.class);
        OssService ossService = mock(OssService.class);
        when(ossService.getOssClient()).thenReturn(ossClient);
        InfiniteCanvasController controller = controller(ossService);

        String url = normalizeInputUrl(controller, "/ai-result/canvas/generated-reference.png");

        assertThat(url).startsWith("https://canvas-input.example/AI_CANVAS/video-reference/").endsWith(".png");
        verify(ossClient).putObject(
                eq("canvas-input"),
                startsWith("AI_CANVAS/video-reference/"),
                any(InputStream.class),
                any(ObjectMetadata.class));
    }

    @Test
    void rejectsMissingOrUnsafeLocalReferenceInsteadOfSendingItToKie() {
        InfiniteCanvasController controller = controller(mock(OssService.class));

        assertThatThrownBy(() -> normalizeInputUrl(controller, "/ai-result/../secret.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或不在本地结果目录");
        assertThatThrownBy(() -> normalizeInputUrl(controller, "/ai-result/canvas/missing.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或不在本地结果目录");
    }

    private InfiniteCanvasController controller(OssService ossService) {
        AppProperties properties = new AppProperties();
        properties.setLocalSaveRoot(localSaveRoot.toString());
        properties.getOss().setInputBucket("canvas-input");
        properties.getOss().setInputPublicHost("https://canvas-input.example");
        return new InfiniteCanvasController(
                mock(CanvasProjectRepository.class),
                mock(CanvasMediaCleanupService.class),
                mock(CanvasTaskService.class),
                mock(KieClientService.class),
                mock(ModelPricingService.class),
                mock(Seedance25VideoRequestService.class),
                mock(TextModelService.class),
                ossService,
                properties,
                new ObjectMapper());
    }

    private String normalizeInputUrl(InfiniteCanvasController controller, String value) {
        try {
            Method method = InfiniteCanvasController.class.getDeclaredMethod("normalizeInputUrl", String.class);
            method.setAccessible(true);
            return (String) method.invoke(controller, value);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
