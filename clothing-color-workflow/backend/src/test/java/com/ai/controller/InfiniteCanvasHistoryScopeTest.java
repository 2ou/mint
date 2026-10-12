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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfiniteCanvasHistoryScopeTest {

    @Test
    void regularAccountCannotOverrideItsHistoryScope() {
        CanvasTaskService taskService = mock(CanvasTaskService.class);
        InfiniteCanvasController controller = controller(taskService);
        MockHttpServletRequest request = request("SHOP-A", "Alice");
        when(taskService.adminSearchTasks("SHOP-A", "Alice", "12", "image", "success", 2, 50))
                .thenReturn(searchResult(List.of("SHOP-A", "SHOP-B")));

        Map<String, Object> result = controller.getCanvasTaskHistory(
                "SHOP-B", "Bob", "12", "image", "success", 2, 50, request);

        verify(taskService).adminSearchTasks("SHOP-A", "Alice", "12", "image", "success", 2, 50);
        assertThat(result.get("scope")).isEqualTo("account");
        assertThat(result.get("shops")).isEqualTo(List.of("SHOP-A"));
    }

    @Test
    void superAdministratorCanQueryPlatformHistory() {
        CanvasTaskService taskService = mock(CanvasTaskService.class);
        InfiniteCanvasController controller = controller(taskService);
        MockHttpServletRequest request = request("PINKSIR", "Administrator");
        when(taskService.adminSearchTasks("SHOP-B", "Bob", null, null, null, 1, 100))
                .thenReturn(searchResult(List.of("SHOP-A", "SHOP-B")));

        Map<String, Object> result = controller.getCanvasTaskHistory(
                "SHOP-B", "Bob", null, null, null, 1, 100, request);

        verify(taskService).adminSearchTasks("SHOP-B", "Bob", null, null, null, 1, 100);
        assertThat(result.get("scope")).isEqualTo("platform");
        assertThat(result.get("shops")).isEqualTo(List.of("SHOP-A", "SHOP-B"));
    }

    private InfiniteCanvasController controller(CanvasTaskService taskService) {
        return new InfiniteCanvasController(
                mock(CanvasProjectRepository.class),
                mock(CanvasMediaCleanupService.class),
                taskService,
                mock(KieClientService.class),
                mock(ModelPricingService.class),
                mock(Seedance25VideoRequestService.class),
                mock(TextModelService.class),
                mock(OssService.class),
                new AppProperties(),
                new ObjectMapper());
    }

    private MockHttpServletRequest request(String shopName, String operator) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("shopName", shopName);
        request.setAttribute("operator", operator);
        return request;
    }

    private Map<String, Object> searchResult(List<String> shops) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tasks", List.of());
        response.put("summary", Map.of());
        response.put("shops", shops);
        response.put("total", 0L);
        return response;
    }
}
