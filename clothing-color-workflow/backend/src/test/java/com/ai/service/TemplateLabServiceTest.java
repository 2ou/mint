package com.ai.service;

import com.ai.config.AppProperties;
import com.ai.dto.TemplateLabProjectCreateRequest;
import com.ai.entity.TemplateLabProject;
import com.ai.exception.BusinessException;
import com.ai.repository.TemplateLabProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TemplateLabServiceTest {

    private TemplateLabProjectRepository repository;
    private TemplateLabService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TemplateLabProjectRepository.class);
        service = new TemplateLabService(
                repository,
                new ObjectMapper(),
                Mockito.mock(OssService.class),
                new AppProperties());
        service.loadTemplates();
    }

    @Test
    void loadsFiveBuiltInTemplates() {
        assertEquals(5, service.listTemplates().size());
        assertTrue(service.listTemplates().stream().allMatch(template -> template.path("frames").isArray()));
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
        assertEquals(1500, response.getCanvasHeight());
        assertFalse(response.getProjectName().isBlank());
    }

    @Test
    void rejectsProjectOwnedByAnotherUser() {
        when(repository.findByIdAndOwnerUserId(12L, 9L)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.getProject(12L, 9L));

        assertEquals("拼图项目不存在或无权访问", error.getMessage());
    }
}
