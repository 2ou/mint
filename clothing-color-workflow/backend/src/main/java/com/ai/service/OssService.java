package com.ai.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {

    // 上传原图/颜色图
    String uploadInput(String spu, String type, MultipartFile file);

    // 上传结果到 OSS（默认临时桶，permanent=true 时走永久桶）
    String uploadResultToOss(String spu, String resultUrl, Long taskId);
    String uploadResultToOss(String spu, String resultUrl, Long taskId, boolean permanent);

    // 🔴 新增：直接将 File 对象上传到 OSS 的方法
    String uploadFileToOss(String spu, java.io.File file);

    // 保存结果到本地
    String saveResultToLocal(String spu, String resultUrl, String localRootPath);

    // 🔴 新增这一行：暴露 OSS 客户端供外部调用
    com.aliyun.oss.OSS getOssClient();
}