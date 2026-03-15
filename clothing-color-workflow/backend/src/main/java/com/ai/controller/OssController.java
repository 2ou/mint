package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.dto.ApiResponse;
import com.aliyun.oss.OSS;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阿里云 OSS 客户端直传控制器
 * 职责：给前端发放临时的上传签名，让前端绕过服务器直接把图片传给阿里云
 */
@RestController
@RequestMapping("/api/oss")
@CrossOrigin // 必须允许跨域，否则前端浏览器会拦截请求
@RequiredArgsConstructor
public class OssController {

    private final OSS ossClient;
    private final AppProperties appProperties;

    /**
     * 获取 OSS 临时上传凭证 (Policy)
     * * @return 包含 accessid, policy, signature, host 等签名字段的 Map，前端拿着这些参数直接 POST 到阿里云
     */
    @GetMapping("/policy")
    public ApiResponse<Map<String, String>> getPolicy() {
        AppProperties.Oss oss = appProperties.getOss();
        String host = oss.getInputPublicHost();

        // 动态生成按天分类的文件夹，防止所有文件堆在根目录 (如: direct-upload/20231025/)
        String dir = "direct-upload/" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/";

        // 签名有效期设置为 5 分钟 (300,000 毫秒)
        long expireEndTime = System.currentTimeMillis() + 300 * 1000;

        PolicyConditions policyConds = new PolicyConditions();
        // 限制单个上传文件大小：最大 1GB (1048576000 字节)
        policyConds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, 1048576000);
        // 限制上传的目录前缀，防止前端乱传到其他目录
        policyConds.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);

        // 利用阿里云 SDK 生成加密的 policy 和 signature
        String postPolicy = ossClient.generatePostPolicy(new Date(expireEndTime), policyConds);
        String encodedPolicy = BinaryUtil.toBase64String(postPolicy.getBytes(StandardCharsets.UTF_8));
        String postSignature = ossClient.calculatePostSignature(postPolicy);

        // 组装前端需要的全部参数
        Map<String, String> respMap = new LinkedHashMap<>();
        respMap.put("accessid", oss.getAccessKeyId());
        respMap.put("policy", encodedPolicy);
        respMap.put("signature", postSignature);
        respMap.put("dir", dir);
        respMap.put("host", host);

        return ApiResponse.ok("ok", respMap);
    }
}