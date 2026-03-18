package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.service.OssService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.*;

@Service
@Slf4j
public class OssServiceImpl implements OssService {

    private final AppProperties appProperties;
    private final OSS ossClient;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    // 🔴 终极防御核心：建立一个有 10 个护士的“隔离线程池”
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
    public String uploadResultToOss(String spu, String resultUrl) {
        // 只需 1 个临时文件，因为我们不做压缩了
        final File[] tempFiles = new File[1];

        try {
            log.info("【OSS转存-1】开始处理转存，目标链接: {}", resultUrl);
            AppProperties.Oss oss = appProperties.getOss();

            // 保持原图的后缀名（大概率是 .png）
            String extension = resultUrl.toLowerCase().contains(".jpg") ? ".jpg" : ".png";
            String objectName = spu + "/result/AI_" + System.currentTimeMillis() + extension;

            Callable<String> isolationTask = () -> {
                log.info("【OSS转存-2】创建临时文件...");
                tempFiles[0] = File.createTempFile("ai_result_raw_", extension);

                log.info("【OSS转存-3】(隔离线程) 开始下载远程图片...");
                Request request = new Request.Builder()
                        .url(resultUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new RuntimeException("HTTP 异常: " + response.code());
                    if (response.body() == null) throw new RuntimeException("返回内容为空！");

                    try (InputStream in = response.body().byteStream();
                         FileOutputStream out = new FileOutputStream(tempFiles[0])) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                log.info("【OSS转存-4】✅ 下载完成！大小: {} KB", tempFiles[0].length() / 1024);

                // 🔴 核心改动：砍掉极度吃 CPU 和内存的压缩代码，直接上传原图！
                log.info("【OSS转存-5】直接调用阿里云 SDK 上传原图...");
                ossClient.putObject(oss.getResultBucket(), objectName, tempFiles[0]);
                log.info("【OSS转存-6】🎉 阿里云 OSS 上传成功！");

                return oss.getResultPublicHost() + "/" + objectName;
            };

            Future<String> future = isolationPool.submit(isolationTask);

            try {
                // 🔴 核心改动：斩杀线从 80 秒放宽到 180 秒（3分钟），给小水管服务器充足的时间！
                return future.get(180, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("服务器带宽受限或对方网络极慢，耗时超 180 秒被强杀！");
            }

        } catch (Throwable e) {
            log.error("【OSS转存-兜底捕获】任务失败或被斩杀: {}", e.getMessage());
            throw new RuntimeException("图片抓取并转存失败：" + e.getMessage(), e);
        } finally {
            log.info("【OSS转存-7】清理服务器硬盘的临时文件...");
            if (tempFiles[0] != null && tempFiles[0].exists()) tempFiles[0].delete();
        }
    }

    @Override
    public String saveResultToLocal(String spu, String resultUrl, String localRoot) {
        return null; // 此方法已废弃，无需理会
    }

    // 🔴 新增这个方法来返回底层的 ossClient
    @Override
    public com.aliyun.oss.OSS getOssClient() {
        // 注意：这里的 ossClient 必须是您在这个类最上方定义的那个阿里云 OSS 客户端变量名。
        // 一般来说就叫 ossClient。如果您的叫其他名字（比如 aliyunOss），请替换掉它。
        return this.ossClient;
    }
}