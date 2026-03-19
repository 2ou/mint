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

    // 🔴 全局网络超时配置
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)  // 🔴 读超时：允许 KIE 服务器发呆 10 分钟不传数据
            .writeTimeout(300, TimeUnit.SECONDS)
            .callTimeout(5000, TimeUnit.SECONDS) // 🔴 总下载时间底线：5000S ！
            .build();

    // 终极防御核心：建立一个有 10 个护士的“隔离线程池”
    private final ExecutorService isolationPool = Executors.newFixedThreadPool(20);

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
            if (localRoot == null) {
                // 自动判断系统：如果是 Windows 就用 D 盘，否则（Linux/Mac）用用户目录下的文件夹
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    localRoot = "D:/AiResult";
                } else {
                    localRoot = "/tmp/ai-result"; // 或者其他 Linux 路径
                }
            }

            String extension = resultUrl.toLowerCase().contains(".jpg") ? ".jpg" : ".png";
            // 加入一段 8 位的随机 UUID，确保并发时绝对不会重名
            String fileName = System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + extension;

            File targetDir = new File(localRoot + "/" + spu);
            if (!targetDir.exists()) {
                targetDir.mkdirs(); // 自动创建多级目录
            }

            File permanentFile = new File(targetDir, fileName);

            Callable<String> isolationTask = () -> {
                log.info("【双保险】正在下载到本地: {}", permanentFile.getAbsolutePath());

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(resultUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                        .build();

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new RuntimeException("KIE图片下载失败: HTTP " + response.code());
                    }

                    // 🔴 修改为引入 Thumbnailator 压缩机制
                    try (InputStream in = response.body().byteStream()) {
                        net.coobird.thumbnailator.Thumbnails.of(in)
                                .scale(1.0)         // 保持原图的宽高分辨率不变 (例如依然是 4K)
                                .outputQuality(0.9) // 🔴 核心：画质压缩为原来的 90%
                                .toFile(permanentFile); // 直接输出到本地文件
                    } catch (Exception streamEx) {
                        // 如果下到一半断开了，文件已经生成了但只有半截（会导致黑屏）
                        // 必须立刻将其销毁，防止残次品流入后续环节！
                        if (permanentFile.exists()) {
                            permanentFile.delete();
                            log.error("【残次品拦截】下载中途数据流中断，已销毁半截图片: {}", permanentFile.getName());
                        }
                        throw new RuntimeException("网络流中断，图片下载不完整", streamEx);
                    }
                }

                if (!permanentFile.exists() || permanentFile.length() == 0) {
                    throw new RuntimeException("本地保存文件失败，文件未生成或大小为0");
                }

                log.info("【本地保存成功】图片已稳妥躺在硬盘，准备尝试上传 OSS...");

                String finalLocalPath = permanentFile.getAbsolutePath();
                String ossUrl = "";

                try {
                    File fileToUpload = permanentFile.getAbsoluteFile();
                    String objectName = spu + "/result/" + fileName;

                    ossClient.putObject(oss.getResultBucket(), objectName, fileToUpload);
                    ossUrl = oss.getResultPublicHost() + "/" + objectName;

                    log.info("【OSS上传成功】链接: {}", ossUrl);
                } catch (Exception ossEx) {
                    log.error("【OSS上传降级】本地已保存，但上传云端失败: {}", ossEx.getMessage());
                }

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

                return ossUrl + "|" + finalLocalPath;
            };

            future = isolationPool.submit(isolationTask);
            // 🔴 配合上面的 1 小时 (3600秒) 总超时，这里护士的等待时间给到 3610 秒
            return future.get(3610, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            log.error("【转存严重超时】下载动作耗时超过 1 小时，已强制终止该线程！");
            throw new RuntimeException("图片下载严重超时 (超1小时)");
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