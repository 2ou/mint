package com.ai.creative.asset.convert;

import com.ai.creative.asset.dto.resp.AssetDetailResp;
import com.ai.creative.asset.dto.resp.AssetPageResp;
import com.ai.creative.asset.entity.CreativeAsset;
import org.springframework.beans.BeanUtils;

public class AssetConvert {
    public static AssetDetailResp toDetail(CreativeAsset e){ AssetDetailResp r = new AssetDetailResp(); BeanUtils.copyProperties(e,r); return r; }
    public static AssetPageResp toPage(CreativeAsset e){ AssetPageResp r = new AssetPageResp(); BeanUtils.copyProperties(e,r); return r; }
}
