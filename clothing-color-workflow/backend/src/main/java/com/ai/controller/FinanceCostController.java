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
import java.util.Map;

/** Finance-only, platform-wide fixed-cost reporting endpoints. */
@RestController
@RequestMapping("/api/admin/finance")
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
        requireFinanceAdmin(request);
        FinanceCostFilter filter = new FinanceCostFilter(startDate, endDate, module, shopName, operator,
                mediaType, model, keyword);
        return ApiResponse.ok("ok", financeCostService.report(filter, page, size));
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
        requireFinanceAdmin(request);
        FinanceCostFilter filter = new FinanceCostFilter(startDate, endDate, module, shopName, operator,
                mediaType, model, keyword);
        String filename = "AI平台费用报表_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        financeCostService.exportWorkbook(filter, response.getOutputStream());
    }

    private void requireFinanceAdmin(HttpServletRequest request) {
        Object value = request.getAttribute("shopName");
        String shopName = value == null ? "" : String.valueOf(value).trim();
        if (!"PINKSIR".equalsIgnoreCase(shopName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可以查看平台财务数据");
        }
    }
}
