package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.FinanceCostFilter;
import com.ai.service.FinanceCostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceCostControllerTest {

    @Mock
    private FinanceCostService financeCostService;

    private FinanceCostController controller;

    @BeforeEach
    void setUp() {
        controller = new FinanceCostController(financeCostService);
    }

    @Test
    void regularAccountIsAlwaysScopedToAuthenticatedShopAndOperator() {
        when(financeCostService.report(any(), anyInt(), anyInt())).thenReturn(new LinkedHashMap<>());
        MockHttpServletRequest request = request("SHOP-A", "Alice");

        ApiResponse<Map<String, Object>> response = controller.costs(
                null, null, null, "OTHER-SHOP", "Mallory", null, null, null,
                1, 50, request
        );

        ArgumentCaptor<FinanceCostFilter> filter = ArgumentCaptor.forClass(FinanceCostFilter.class);
        verify(financeCostService).report(filter.capture(), anyInt(), anyInt());
        assertThat(filter.getValue().shopName()).isEqualTo("SHOP-A");
        assertThat(filter.getValue().operator()).isEqualTo("Alice");
        assertThat(response.getData()).containsEntry("scope", "account");
    }

    @Test
    void pinksirCanKeepPlatformFilters() {
        when(financeCostService.report(any(), anyInt(), anyInt())).thenReturn(new LinkedHashMap<>());
        MockHttpServletRequest request = request("PINKSIR", "Finance Admin");

        ApiResponse<Map<String, Object>> response = controller.costs(
                null, null, null, "SHOP-B", "Bob", null, null, null,
                1, 50, request
        );

        ArgumentCaptor<FinanceCostFilter> filter = ArgumentCaptor.forClass(FinanceCostFilter.class);
        verify(financeCostService).report(filter.capture(), anyInt(), anyInt());
        assertThat(filter.getValue().shopName()).isEqualTo("SHOP-B");
        assertThat(filter.getValue().operator()).isEqualTo("Bob");
        assertThat(response.getData()).containsEntry("scope", "platform");
    }

    @Test
    void regularAccountExportUsesTheSameServerSideScope() throws Exception {
        MockHttpServletRequest request = request("SHOP-A", "Alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(null, null, null, "OTHER-SHOP", "Mallory", null, null, null,
                request, response);

        ArgumentCaptor<FinanceCostFilter> filter = ArgumentCaptor.forClass(FinanceCostFilter.class);
        verify(financeCostService).exportWorkbook(filter.capture(), any());
        assertThat(filter.getValue().shopName()).isEqualTo("SHOP-A");
        assertThat(filter.getValue().operator()).isEqualTo("Alice");
        assertThat(response.getHeader("Content-Disposition")).contains("AI%E8%B4%A6%E5%8F%B7%E8%B4%B9%E7%94%A8%E6%8A%A5%E8%A1%A8");
    }

    private MockHttpServletRequest request(String shopName, String operator) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("shopName", shopName);
        request.setAttribute("operator", operator);
        return request;
    }
}
