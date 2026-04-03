package com.ai.creative.run.service;

import com.ai.creative.run.dto.req.NodeRunContinueReq;
import com.ai.creative.run.dto.req.NodeRunReq;
import com.ai.creative.run.dto.req.NodeRunSelectOutputReq;
import com.ai.creative.run.dto.resp.NodeRunDetailResp;
import com.ai.creative.run.dto.resp.NodeRunPageResp;

import java.util.List;

public interface NodeRunService {
    NodeRunDetailResp runNode(NodeRunReq req);
    NodeRunDetailResp continueFromNode(NodeRunContinueReq req);
    NodeRunDetailResp detail(Long runId);
    List<NodeRunPageResp> listByProject(Long projectId);
    void selectOutput(Long runId, NodeRunSelectOutputReq req);
}
