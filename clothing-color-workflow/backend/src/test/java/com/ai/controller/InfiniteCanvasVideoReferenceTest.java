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
import org.springframework.mock.web.MockHttpServletResponse;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

    @Test
    void servesCanvasPreviewAtRequestedWidthWithoutChangingOriginal() throws Exception {
        Path imagePath = localSaveRoot.resolve("canvas/large-preview.png");
        Files.createDirectories(imagePath.getParent());
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(new Color(32, 96, 180));
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        ImageIO.write(source, "png", imagePath.toFile());
        source.flush();

        InfiniteCanvasController controller = controller(mock(OssService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.mediaPreview("/ai-result/canvas/large-preview.png", 120, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=86400");
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
        assertThat(preview.getWidth()).isEqualTo(120);
        assertThat(preview.getHeight()).isEqualTo(60);
        preview.flush();
        BufferedImage original = ImageIO.read(imagePath.toFile());
        assertThat(original.getWidth()).isEqualTo(800);
        assertThat(original.getHeight()).isEqualTo(400);
        original.flush();
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
