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

    // AI 画布：仅把 KIE 结果下载到本地硬盘（不上传 OSS），返回本地绝对路径；失败返回 null
    String downloadResultToLocal(String taskId, String resultUrl);

    // 同名重载：指定子目录（canvas / aplus / tasks / models ...），便于各模块结果分区存放
    String downloadResultToLocal(String subDir, String taskId, String resultUrl);

    // 把本地落盘的绝对路径转成前端可访问的服务 URL（/ai-result/** 由 WebMvcConfig 静态映射）；不在 localSaveRoot 之下返回 null
    String localServingUrl(String absolutePath);

    // 🔴 新增这一行：暴露 OSS 客户端供外部调用
    com.aliyun.oss.OSS getOssClient();
}