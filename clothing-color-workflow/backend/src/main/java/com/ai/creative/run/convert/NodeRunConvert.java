package com.ai.creative.run.convert;

import com.ai.creative.run.dto.resp.NodeRunDetailResp;
import com.ai.creative.run.dto.resp.NodeRunPageResp;
import com.ai.creative.run.entity.CreativeNodeRun;
import org.springframework.beans.BeanUtils;

public class NodeRunConvert {
    public static NodeRunDetailResp toDetail(CreativeNodeRun e){ NodeRunDetailResp r = new NodeRunDetailResp(); BeanUtils.copyProperties(e,r); return r; }
    public static NodeRunPageResp toPage(CreativeNodeRun e){ NodeRunPageResp r = new NodeRunPageResp(); BeanUtils.copyProperties(e,r); return r; }
}
