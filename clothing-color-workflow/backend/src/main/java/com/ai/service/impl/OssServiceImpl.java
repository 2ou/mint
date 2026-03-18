package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.service.OssService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.*;

@Service
@Slf4j
public class OssServiceImpl implements OssService {

    private final AppProperties appProperties;
    private final OSS ossClient;

    // 🔴 全局网络超时配置，放宽到 30 分钟 (1800秒)
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)  // 读写间隔允许卡顿 5 分钟
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(1800, TimeUnit.SECONDS) // 🔴 总下载时间底线：30 分钟 (1800秒)！
            .build();

    // 终极防御核心：建立一个有 10 个护士的“隔离线程池”
    private final ExecutorService isolationPool = Executors.newFixedThreadPool(10);

    public OssServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
        AppProperties.Oss oss = appProperties.getOss();
        if (oss == null) {
            throw new RuntimeException("OSS 配置未注入，请检查 application.yml");
        }
        this.ossClient = new OSSClientBuilder().build(
                oss.getEndpoint(),
                oss.getAccessKeyId(),
                oss.getAccessKeySecret()
        );
    }

    @Override
    public String uploadInput(String spu, String type, MultipartFile file) {
        try {
            AppProperties.Oss oss = appProperties.getOss();
            String objectName = spu + "/" + type + "/" + file.getOriginalFilename();
            ossClient.putObject(oss.getInputBucket(), objectName, file.getInputStream());
            return oss.getInputPublicHost() + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("上传输入文件失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String uploadFileToOss(String spu, File file) {
        try {
            AppProperties.Oss oss = appProperties.getOss();
            String objectName = spu + "/result/" + file.getName();

            log.info("【修复上传】正在将本地文件上传至 OSS: {}", objectName);
            ossClient.putObject(oss.getResultBucket(), objectName, file);

            return oss.getResultPublicHost() + "/" + objectName;
        } catch (Exception e) {
            log.error("【修复上传失败】文件: {}, 原因: {}", file.getAbsolutePath(), e.getMessage());
            throw new RuntimeException("上传本地备份到 OSS 失败", e);
        }
    }

    @Override
    public String uploadResultToOss(String spu, String resultUrl) {
        Future<String> future = null;

        try {
            log.info("【双保险转存】开始处理，目标链接: {}", resultUrl);
            AppProperties.Oss oss = appProperties.getOss();

            String localRoot = appProperties.getLocalSaveRoot();
            if (localRoot == null) localRoot = "D:/AiResult";

            String extension = resultUrl.toLowerCase().contains(".jpg") ? ".jpg" : ".png";
            String fileName = System.currentTimeMillis() + extension;

            File targetDir = new File(localRoot + "/" + spu);
            if (!targetDir.exists()) {
                targetDir.mkdirs(); // 自动创建多级目录
            }

            File permanentFile = new File(targetDir, fileName);

            Callable<String> isolationTask = () -> {
                log.info("【双保险】正在下载到本地: {}", permanentFile.getAbsolutePath());

                // 🔴 步骤 1：下载 KIE 图片到本地硬盘
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(resultUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                        .build();

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new RuntimeException("KIE图片下载失败: HTTP " + response.code());
                    }
                    try (InputStream in = response.body().byteStream();
                         FileOutputStream out = new FileOutputStream(permanentFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                }

                // 下载完成后进行校验，这一步如果报错，说明本地也没存下来，直接抛异常阻断
                if (!permanentFile.exists() || permanentFile.length() == 0) {
                    throw new RuntimeException("本地保存文件失败，文件未生成或大小为0");
                }

                log.info("【本地保存成功】图片已稳妥躺在硬盘，准备尝试上传 OSS...");

                String finalLocalPath = permanentFile.getAbsolutePath();
                String ossUrl = "";

                // 🔴 步骤 2：解绑 OSS 上传！单独套上 try-catch
                try {
                    File fileToUpload = permanentFile.getAbsoluteFile();
                    String objectName = spu + "/result/" + fileName;

                    ossClient.putObject(oss.getResultBucket(), objectName, fileToUpload);
                    ossUrl = oss.getResultPublicHost() + "/" + objectName;

                    log.info("【OSS上传成功】链接: {}", ossUrl);
                } catch (Exception ossEx) {
                    // ⚠️ 核心：仅仅打印日志，绝不抛出异常！保护已经成功的 finalLocalPath！
                    log.error("【OSS上传降级】本地已保存，但上传云端失败: {}", ossEx.getMessage());
                }

                // 🔴 步骤 3：智能清理。只有 OSS 成功了 (ossUrl 不为空)，才允许删除本地文件
                if (appProperties.isDeleteLocalAfterUpload()) {
                    if (!ossUrl.isEmpty()) {
                        boolean deleted = permanentFile.delete();
                        log.info("【正式环境】OSS 上传成功，已自动清理本地图片: {}，删除状态: {}", finalLocalPath, deleted);
                        finalLocalPath = "DELETED";
                    } else {
                        log.warn("【正式环境】因 OSS 上传失败，强制保留本地图片不予删除: {}", finalLocalPath);
                    }
                } else {
                    log.info("【开发环境】本地图片已保留: {}", finalLocalPath);
                }

                // 返回拼接结果，如果 OSS 失败，ossUrl 就是空字符串，Service 层照样能拿到本地路径
                return ossUrl + "|" + finalLocalPath;
            };

            // 🔴 提交任务到隔离池，并设置 1810 秒的底线等待时间 (30分钟 + 10秒缓冲)
            future = isolationPool.submit(isolationTask);
            return future.get(1810, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            // 🔴 日志文案也顺手改一下
            log.error("【转存严重超时】下载动作耗时超过 30 分钟，已强制终止该线程！");
            throw new RuntimeException("图片下载严重超时 (超30分钟)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.error("【本地保存失败】: {}", cause != null ? cause.getMessage() : e.getMessage());
            throw new RuntimeException("本地保存任务失败: " + (cause != null ? cause.getMessage() : "未知"));
        } catch (Exception e) {
            log.error("【转存系统异常】: {}", e.getMessage());
            throw new RuntimeException("发生系统异常", e);
        }
    }

    @Override
    public String saveResultToLocal(String spu, String resultUrl, String localRoot) {
        return null; // 此方法已废弃
    }

    @Override
    public com.aliyun.oss.OSS getOssClient() {
        return this.ossClient;
    }

    @PreDestroy
    public void onDestroy() {
        log.info("【系统关闭】正在释放 OSS 客户端和线程池资源...");
        if (ossClient != null) {
            ossClient.shutdown();
        }
        if (isolationPool != null) {
            isolationPool.shutdown();
        }
    }
}