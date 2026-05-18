package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.dto.ApiResponse;
import com.aliyun.oss.OSS;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
     * 🔴 新增 type 参数：
     * 不传或传 temp -> 走临时桶 (inputBucket, 5天删)
     * 传 permanent -> 走永久桶 (resultBucket, 永久保留)
     */
    @GetMapping("/policy")
    public ApiResponse<Map<String, String>> getPolicy(@RequestParam(value = "type", defaultValue = "temp") String type) {
        AppProperties.Oss oss = appProperties.getOss();

        // 根据 type 动态决定使用哪个桶的参数
        boolean isPermanent = "permanent".equalsIgnoreCase(type);
        String host = isPermanent ? oss.getResultPublicHost() : oss.getInputPublicHost();

        // 动态生成目录 (色卡走 color-cards 目录，任务走 direct-upload)
        String baseDir = isPermanent ? "color-cards/" : "direct-upload/";
        String dir = baseDir + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/";

        long expireEndTime = System.currentTimeMillis() + 300 * 1000;

        PolicyConditions policyConds = new PolicyConditions();
        policyConds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, 1048576000);
        policyConds.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);

        String postPolicy = ossClient.generatePostPolicy(new Date(expireEndTime), policyConds);
        String encodedPolicy = BinaryUtil.toBase64String(postPolicy.getBytes(StandardCharsets.UTF_8));
        String postSignature = ossClient.calculatePostSignature(postPolicy);

        Map<String, String> respMap = new LinkedHashMap<>();
        respMap.put("accessid", oss.getAccessKeyId());
        respMap.put("policy", encodedPolicy);
        respMap.put("signature", postSignature);
        respMap.put("dir", dir);
        respMap.put("host", host);

        return ApiResponse.ok("ok", respMap);
    }

    /**
     * 🔴 新增：将临时桶中的图片跨桶复制到永久桶中
     */
    @PostMapping("/copy-to-permanent")
    public ApiResponse<String> copyToPermanent(@RequestBody Map<String, String> body) {
        String sourceUrl = body.get("url");
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            return ApiResponse.fail("链接不能为空");
        }

        AppProperties.Oss oss = appProperties.getOss();
        String tempHost = oss.getInputPublicHost();
        String permHost = oss.getResultPublicHost();

        // 如果不是临时桶的链接（说明可能是别的网图或已是永久图），直接原样放行
        if (!sourceUrl.startsWith(tempHost)) {
            return ApiResponse.ok("ok", sourceUrl);
        }

        try {
            // 1. 提取出临时桶中的真实 objectKey (去除域名和开头的 /)
            String sourceKey = sourceUrl.replace(tempHost, "");
            if (sourceKey.startsWith("/")) sourceKey = sourceKey.substring(1);
            // 去除图片后可能带有的缩放参数 (?x-oss-process=...)
            if (sourceKey.contains("?")) sourceKey = sourceKey.substring(0, sourceKey.indexOf("?"));

            // 对中文路径进行 URL 解码，防止找不到文件
            sourceKey = java.net.URLDecoder.decode(sourceKey, StandardCharsets.UTF_8.name());

            // 2. 构造永久桶的新路径 (统一放在 templates/ 目录下)
            String ext = sourceKey.contains(".") ? sourceKey.substring(sourceKey.lastIndexOf(".")) : ".png";
            String destKey = "templates/copied_" + System.currentTimeMillis() + ext;

            // 3. 🚀 召唤阿里云进行内网秒级拷贝 (从源桶 到 目标桶)
            ossClient.copyObject(oss.getInputBucket(), sourceKey, oss.getResultBucket(), destKey);

            // 4. 组装并返回永久桶的新链接
            String permanentUrl = permHost + "/" + destKey;
            return ApiResponse.ok("ok", permanentUrl);

        } catch (Exception e) {
            return ApiResponse.fail("跨桶转存失败: " + e.getMessage());
        }
    }
}