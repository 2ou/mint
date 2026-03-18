package com.ai.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {

    // 上传原图/颜色图
    String uploadInput(String spu, String type, MultipartFile file);

    // 上传结果到 OSS
    String uploadResultToOss(String spu, String resultUrl);

    // 保存结果到本地
    String saveResultToLocal(String spu, String resultUrl, String localRootPath);

    // 🔴 新增这一行：暴露 OSS 客户端供外部调用
    com.aliyun.oss.OSS getOssClient();
}