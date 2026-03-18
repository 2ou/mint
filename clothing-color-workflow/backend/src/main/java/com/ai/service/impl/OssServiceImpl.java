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
        // 使用数组包裹临时文件，以便在外层的 finally 中能够安全清理
        final File[] tempFiles = new File[2];

        try {
            log.info("【OSS转存-1】开始处理转存，目标链接: {}", resultUrl);
            AppProperties.Oss oss = appProperties.getOss();
            String extension = ".jpg";
            String objectName = spu + "/result/AI_" + System.currentTimeMillis() + extension;

            // 🔴 建立隔离任务：把所有可能有网络风险的操作，全部关进隔离病房
            Callable<String> isolationTask = () -> {
                log.info("【OSS转存-2】创建临时文件...");
                tempFiles[0] = File.createTempFile("ai_result_raw_", ".png");

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

                log.info("【OSS转存-5】开始进行无损压缩...");
                tempFiles[1] = File.createTempFile("ai_result_compressed_", extension);
                net.coobird.thumbnailator.Thumbnails.of(tempFiles[0])
                        .scale(1.0f)
                        .outputQuality(0.8f)
                        .outputFormat("jpg")
                        .toFile(tempFiles[1]);
                log.info("【OSS转存-6】✅ 压缩完成！大小: {} KB", tempFiles[1].length() / 1024);

                log.info("【OSS转存-7】调用阿里云 SDK 上传...");
                ossClient.putObject(oss.getResultBucket(), objectName, tempFiles[1]);
                log.info("【OSS转存-8】🎉 阿里云 OSS 上传成功！");

                return oss.getResultPublicHost() + "/" + objectName;
            };

            // 🔴 将任务丢进线程池执行，拿到一个 Future (控制权)
            Future<String> future = isolationPool.submit(isolationTask);

            try {
                // 🔴 绝对斩杀线：主线程就在这里等 80 秒，多 1 秒都不等！
                return future.get(80, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // 80 秒没出结果？直接拔管，强杀隔离线程！
                future.cancel(true);
                throw new RuntimeException("底层网络遭遇极度卡死，耗时超 80 秒，触发系统绝对斩杀防线！");
            }

        } catch (Throwable e) {
            log.error("【OSS转存-兜底捕获】任务失败或被斩杀: {}", e.getMessage());
            throw new RuntimeException("图片抓取、压缩并转存失败：" + e.getMessage(), e);
        } finally {
            log.info("【OSS转存-9】清理服务器硬盘的临时文件...");
            // 因为终于能保证主线程百分百会执行到这里，所以硬盘永远不会塞满
            if (tempFiles[0] != null && tempFiles[0].exists()) tempFiles[0].delete();
            if (tempFiles[1] != null && tempFiles[1].exists()) tempFiles[1].delete();
        }
    }

    @Override
    public String saveResultToLocal(String spu, String resultUrl, String localRoot) {
        return null; // 此方法已废弃，无需理会
    }
}