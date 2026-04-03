package com.ai.creative.template.convert;

import com.ai.creative.template.dto.resp.TemplateDetailResp;
import com.ai.creative.template.dto.resp.TemplatePageResp;
import com.ai.creative.template.entity.CreativeTemplate;
import org.springframework.beans.BeanUtils;

public class TemplateConvert {
    public static TemplateDetailResp toDetail(CreativeTemplate e){ TemplateDetailResp r = new TemplateDetailResp(); BeanUtils.copyProperties(e,r); return r; }
    public static TemplatePageResp toPage(CreativeTemplate e){ TemplatePageResp r = new TemplatePageResp(); BeanUtils.copyProperties(e,r); return r; }
}
