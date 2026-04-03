package com.ai.creative.run.controller;

import com.ai.creative.run.dto.req.NodeRunContinueReq;
import com.ai.creative.run.dto.req.NodeRunReq;
import com.ai.creative.run.dto.req.NodeRunSelectOutputReq;
import com.ai.creative.run.dto.resp.NodeRunDetailResp;
import com.ai.creative.run.dto.resp.NodeRunPageResp;
import com.ai.creative.run.service.NodeRunService;
import com.ai.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/creative/runs")
@RequiredArgsConstructor
public class NodeRunController {
    private final NodeRunService nodeRunService;

    @PostMapping("/node")
    public ApiResponse<NodeRunDetailResp> runNode(@RequestBody @Valid NodeRunReq req){ return ApiResponse.ok("ok", nodeRunService.runNode(req)); }
    @PostMapping("/from-node")
    public ApiResponse<NodeRunDetailResp> continueFromNode(@RequestBody @Valid NodeRunContinueReq req){ return ApiResponse.ok("ok", nodeRunService.continueFromNode(req)); }
    @GetMapping("/{runId}")
    public ApiResponse<NodeRunDetailResp> detail(@PathVariable Long runId){ return ApiResponse.ok("ok", nodeRunService.detail(runId)); }
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<NodeRunPageResp>> list(@PathVariable Long projectId){ return ApiResponse.ok("ok", nodeRunService.listByProject(projectId)); }
    @PostMapping("/{runId}/select-output")
    public ApiResponse<Void> select(@PathVariable Long runId, @RequestBody @Valid NodeRunSelectOutputReq req){ nodeRunService.selectOutput(runId, req); return ApiResponse.ok("ok", null); }
}
