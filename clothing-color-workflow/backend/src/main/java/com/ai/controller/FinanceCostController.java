package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.dto.FinanceCostFilter;
import com.ai.service.FinanceCostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Account-scoped fixed-cost reporting; PINKSIR receives the platform-wide view. */
@RestController
@RequestMapping({"/api/finance", "/api/admin/finance"})
@RequiredArgsConstructor
public class FinanceCostController {

    private final FinanceCostService financeCostService;

    @GetMapping("/costs")
    public ApiResponse<Map<String, Object>> costs(
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "shop_name", required = false) String shopName,
            @RequestParam(value = "operator", required = false) String operator,
            @RequestParam(value = "media_type", required = false) String mediaType,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            HttpServletRequest request) {
        ViewerScope viewer = requireViewer(request);
        FinanceCostFilter filter = scopeFilter(new FinanceCostFilter(startDate, endDate, module, shopName,
                operator, mediaType, model, keyword), viewer);
        Map<String, Object> report = new LinkedHashMap<>(financeCostService.report(filter, page, size));
        report.put("scope", viewer.platformWide() ? "platform" : "account");
        report.put("viewer", Map.of(
                "shop_name", viewer.shopName(),
                "operator", viewer.operator()
        ));
        return ApiResponse.ok("ok", report);
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "shop_name", required = false) String shopName,
            @RequestParam(value = "operator", required = false) String operator,
            @RequestParam(value = "media_type", required = false) String mediaType,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        ViewerScope viewer = requireViewer(request);
        FinanceCostFilter filter = scopeFilter(new FinanceCostFilter(startDate, endDate, module, shopName,
                operator, mediaType, model, keyword), viewer);
        String reportName = viewer.platformWide() ? "AI平台费用报表_" : "AI账号费用报表_";
        String filename = reportName + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        financeCostService.exportWorkbook(filter, response.getOutputStream());
    }

    private FinanceCostFilter scopeFilter(FinanceCostFilter requested, ViewerScope viewer) {
        if (viewer.platformWide()) return requested;
        return new FinanceCostFilter(
                requested.startDate(), requested.endDate(), requested.module(),
                viewer.shopName(), viewer.operator(), requested.mediaType(),
                requested.model(), requested.keyword()
        );
    }

    private ViewerScope requireViewer(HttpServletRequest request) {
        String shopName = attribute(request, "shopName");
        String operator = attribute(request, "operator");
        if (shopName.isBlank() || operator.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录信息不完整，请重新登录");
        }
        return new ViewerScope(shopName, operator, "PINKSIR".equalsIgnoreCase(shopName));
    }

    private String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ViewerScope(String shopName, String operator, boolean platformWide) {
    }
}
