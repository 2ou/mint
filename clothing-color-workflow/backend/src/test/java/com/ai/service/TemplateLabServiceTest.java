package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.dto.TemplateLabProjectCreateRequest;
import com.ai.dto.TemplateLabTemplateCreateRequest;
import com.ai.dto.KieTaskResult;
import com.ai.entity.TemplateLabPersonalTemplate;
import com.ai.entity.TemplateLabProject;
import com.ai.exception.BusinessException;
import com.ai.repository.TemplateLabPersonalTemplateRepository;
import com.ai.repository.TemplateLabProjectRepository;
import com.aliyun.oss.OSS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateLabServiceTest {

    private TemplateLabProjectRepository repository;
    private TemplateLabPersonalTemplateRepository personalTemplateRepository;
    private OssService ossService;
    private KieClientService kieClientService;
    private CanvasTaskService canvasTaskService;
    private ModelPricingService modelPricingService;
    private TemplateLabService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TemplateLabProjectRepository.class);
        personalTemplateRepository = Mockito.mock(TemplateLabPersonalTemplateRepository.class);
        ossService = Mockito.mock(OssService.class);
        kieClientService = Mockito.mock(KieClientService.class);
        canvasTaskService = Mockito.mock(CanvasTaskService.class);
        modelPricingService = Mockito.mock(ModelPricingService.class);
        when(personalTemplateRepository.findByOwnerUserIdOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        service = new TemplateLabService(
                repository,
                personalTemplateRepository,
                new ObjectMapper(),
                ossService,
                new AppProperties(),
                kieClientService,
                canvasTaskService,
                modelPricingService);
        service.loadTemplates();
    }

    @Test
    void loadsBuiltInTemplatesWithVersionedProtocol() {
        var templates = service.listTemplates(7L);
        assertTrue(templates.size() >= 10);
        assertTrue(templates.stream().allMatch(template -> template.path("schemaVersion").asInt() == 1));
        assertTrue(templates.stream().allMatch(template -> template.path("frames").isArray() || template.path("images").isArray()));
        assertTrue(templates.stream().allMatch(template -> List.of("副图", "亚马逊 A+")
                .contains(template.path("usageType").asText())));
        assertTrue(templates.stream().allMatch(template -> List.of("1:1", "3:4", "16:9", "2928:1200", "1200:900")
                .contains(template.path("ratioGroup").asText())));
    }

    @Test
    void createsProjectOwnedByCurrentUser() {
        when(repository.save(any(TemplateLabProject.class))).thenAnswer(invocation -> {
            TemplateLabProject project = invocation.getArgument(0);
            project.setId(81L);
            project.setVersion(0L);
            return project;
        });
        TemplateLabProjectCreateRequest request = new TemplateLabProjectCreateRequest();
        request.setTemplateId("fashion-duo");
        request.setProjectName("春夏副图");

        var response = service.createProject(request, 7L, "设计师", "PINKSIR");

        assertEquals(81L, response.getId());
        assertEquals("春夏副图", response.getProjectName());
        assertEquals("fashion-duo", response.getTemplateId());
        assertEquals(1200, response.getCanvasWidth());
        assertEquals(1600, response.getCanvasHeight());
        assertTrue(response.getTemplateDefinitionJson().contains("\"fashion-duo\""));
        assertFalse(response.getProjectName().isBlank());
    }

    @Test
    void savesProjectLayoutAsPersonalTemplateWithoutChangingProject() {
        TemplateLabProject project = new TemplateLabProject();
        project.setId(81L);
        project.setOwnerUserId(7L);
        project.setCanvasWidth(1200);
        project.setCanvasHeight(1600);
        when(repository.findByIdAndOwnerUserId(81L, 7L)).thenReturn(Optional.of(project));
        when(personalTemplateRepository.save(any(TemplateLabPersonalTemplate.class))).thenAnswer(invocation -> {
            TemplateLabPersonalTemplate template = invocation.getArgument(0);
            template.setId(13L);
            return template;
        });
        TemplateLabTemplateCreateRequest request = new TemplateLabTemplateCreateRequest();
        request.setName("我的双图模板");
        request.setCategory("副图");
        request.setDefinitionJson("{\"schemaVersion\":1,\"usageType\":\"副图\",\"ratioGroup\":\"3:4\",\"width\":1200,\"height\":1600,\"frames\":[{\"id\":\"left\",\"width\":500,\"height\":900}],\"texts\":[]}");

        JsonNode template = service.createPersonalTemplate(81L, 7L, request);

        assertEquals("personal:13", template.path("id").asText());
        assertEquals("personal", template.path("source").asText());
        assertEquals("我的双图模板", template.path("name").asText());
    }

    @Test
    void rejectsProjectOwnedByAnotherUser() {
        when(repository.findByIdAndOwnerUserId(12L, 9L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.getProject(12L, 9L));

        assertEquals("拼图项目不存在或无权访问", error.getMessage());
    }

    @Test
    void rejectsPersonalTemplateOutsideSupportedRatios() {
        TemplateLabProject project = new TemplateLabProject();
        project.setId(91L);
        project.setOwnerUserId(7L);
        project.setCanvasWidth(1200);
        project.setCanvasHeight(1500);
        when(repository.findByIdAndOwnerUserId(91L, 7L)).thenReturn(Optional.of(project));
        TemplateLabTemplateCreateRequest request = new TemplateLabTemplateCreateRequest();
        request.setName("旧比例模板");
        request.setDefinitionJson("{\"usageType\":\"副图\",\"ratioGroup\":\"3:4\",\"frames\":[{\"id\":\"hero\",\"width\":500,\"height\":900}],\"texts\":[]}");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createPersonalTemplate(91L, 7L, request));

        assertTrue(error.getMessage().contains("副图仅支持"));
    }

    @Test
    void createsQwenCutoutWithOfficialImageUrls() {
        TemplateLabProject project = new TemplateLabProject();
        project.setId(81L);
        project.setOwnerUserId(7L);
        project.setShopName("PINKSIR");
        when(repository.findByIdAndOwnerUserId(81L, 7L)).thenReturn(Optional.of(project));
        OSS oss = Mockito.mock(OSS.class);
        when(ossService.getOssClient()).thenReturn(oss);
        KieTaskResult created = new KieTaskResult();
        created.setTaskId("task-cutout-1");
        when(kieClientService.createMarketTask(eq("qwen2-1/image-to-image"), any())).thenReturn(created);
        when(canvasTaskService.billingFields("task-cutout-1")).thenReturn(Map.of(
                "estimated_cost", new BigDecimal("0.0320")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "cutout-input.jpg", "image/jpeg", new byte[]{1, 2, 3});

        Map<String, Object> response = service.createCutout(
                81L, 7L, "PINKSIR", "PINKSIR", "image-7", "https://example.com/source.png", file);

        assertEquals("task-cutout-1", response.get("task_id"));
        var inputCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(kieClientService).createMarketTask(eq("qwen2-1/image-to-image"), inputCaptor.capture());
        assertEquals(7, inputCaptor.getValue().size());
        assertTrue(String.valueOf(inputCaptor.getValue().get("prompt")).contains("Remove the background"));
        Object imageInput = inputCaptor.getValue().get("image_urls");
        assertTrue(imageInput instanceof List<?>);
        assertEquals(1, ((List<?>) imageInput).size());
        assertTrue(String.valueOf(((List<?>) imageInput).getFirst())
                .contains("/TEMPLATE_LAB/PINKSIR/7/81/cutout-input/"));
        assertEquals("auto", inputCaptor.getValue().get("aspect_ratio"));
        assertEquals("2K", inputCaptor.getValue().get("resolution"));
        assertEquals("transparent", inputCaptor.getValue().get("background"));
        assertEquals("png", inputCaptor.getValue().get("output_format"));
        assertEquals(false, inputCaptor.getValue().get("enhance_prompt"));
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(canvasTaskService).recordCreated(eq("task-cutout-1"), eq("image"), eq("PINKSIR"), eq("PINKSIR"), payloadCaptor.capture());
        assertEquals("template-lab:81", payloadCaptor.getValue().get("canvas_id"));
        assertEquals("image-7", payloadCaptor.getValue().get("canvas_node_id"));
    }

    @Test
    void rejectsCutoutInputLargerThanFiveMegabytes() {
        TemplateLabProject project = new TemplateLabProject();
        project.setId(81L);
        project.setOwnerUserId(7L);
        when(repository.findByIdAndOwnerUserId(81L, 7L)).thenReturn(Optional.of(project));
        MockMultipartFile file = new MockMultipartFile(
                "file", "too-large.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createCutout(
                81L, 7L, "PINKSIR", "PINKSIR", "image-7", "", file));

        assertTrue(error.getMessage().contains("5MB"));
    }
}
