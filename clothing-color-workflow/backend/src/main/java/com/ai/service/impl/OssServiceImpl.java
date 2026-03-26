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
            .callTimeout(5400, TimeUnit.SECONDS) // 🔴 总下载时间底线：1.5小时 ！
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
    public String uploadResultToOss(String spu, String resultUrl, String resolution) {
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
                    localRoot = "/data/ai-images/tmp"; // 或者其他 Linux 路径
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

                // ✅ 修复：补全满血防盗链伪装，防止被 KIE 服务器拦截
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(resultUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .addHeader("Referer", "https://aiquickdraw.com/")
                        .build();

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new RuntimeException("KIE图片下载失败: HTTP " + response.code());
                    }

                    // 第一步：用最基础的缓冲流，把网络字节原封不动、100%安全地写到本地硬盘
                    try (InputStream in = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(permanentFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception streamEx) {
                        if (permanentFile.exists()) {
                            permanentFile.delete();
                            log.error("【残次品拦截】物理网络中断，已销毁不完整文件: {}", permanentFile.getName());
                        }
                        throw new RuntimeException("底层网络流中断，下载失败", streamEx);
                    }

                    // 🔴 第二步：智能判断分辨率。只有 4K 才进行 90% 画质压缩！
                    if ("4K".equalsIgnoreCase(resolution)) {
                        File tempCompressedFile = new File(permanentFile.getAbsolutePath() + ".tmp");
                        try {
                            net.coobird.thumbnailator.Thumbnails.of(permanentFile)
                                    .scale(1.0)
                                    .outputQuality(0.9) // 4K 图片画质压缩为 90%
                                    .toFile(tempCompressedFile);

                            // 压缩成功，用较小的临时文件替换掉刚下载的原文件 (使用安全的 NIO move)
                            java.nio.file.Files.move(tempCompressedFile.toPath(), permanentFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            log.info("【图片压缩成功】检测到 4K 分辨率，已完成 90% 画质压缩以节省空间");
                        } catch (Exception compressEx) {
                            log.warn("【画质压缩失败】将跳过压缩，直接使用未压缩的 4K 原图: {}", compressEx.getMessage());
                        } finally {
                            // 清理可能残留的临时文件
                            if (tempCompressedFile.exists()) {
                                tempCompressedFile.delete();
                            }
                        }
                    } else {
                        // 2K 或其他分辨率，直接跳过压缩
                        log.info("【跳过压缩】当前任务分辨率为 {}，无需压缩，已保留 100% 原图画质", resolution);
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
                        try {
                            // 🔴 停顿 100 毫秒，确保 OSS 客户端彻底松开文件句柄
                            Thread.sleep(100);

                            // 🔴 放弃愚蠢的 file.delete()，使用 NIO 强力删除！失败了会直接抛出明确的异常！
                            java.nio.file.Files.deleteIfExists(permanentFile.toPath());

                            log.info("【正式环境】OSS 上传成功，已彻底清理本地图片: {}", finalLocalPath);
                            finalLocalPath = "DELETED";
                        } catch (Exception deleteEx) {
                            // 如果删不掉，这里会明确打印是因为【权限不足】还是【文件被占用】！
                            log.error("【正式环境】删除本地文件失败，元凶是: {}", deleteEx.getMessage(), deleteEx);
                        }
                    }
                }

                return ossUrl + "|" + finalLocalPath;
            };

            future = isolationPool.submit(isolationTask);
            // 🔴 配合上面的 1.5 小时 (5400秒) 总超时，这里护士的等待时间给到 5410 秒
            return future.get(5410, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            log.error("【转存严重超时】下载动作耗时超过 1.5 小时，已强制终止该线程！");
            throw new RuntimeException("图片下载严重超时 (超1.5小时)");
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