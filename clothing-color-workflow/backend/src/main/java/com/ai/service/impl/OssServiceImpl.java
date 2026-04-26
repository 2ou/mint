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
    public String uploadResultToOss(String spu, String resultUrl, Long taskId) {
        // 🔴 关键修复：防御空指针
        if (resultUrl == null || resultUrl.trim().isEmpty()) {
            log.error("【转存异常】传入的 resultUrl 为空！任务ID: {}", taskId);
            return null; // 直接返回，中止转存，防止引发系统崩溃
        }

        Future<String> future = null;

        try {
            log.info("【双保险转存】开始处理，任务ID: {}, 目标链接: {}", taskId, resultUrl);
            AppProperties.Oss oss = appProperties.getOss();

            // 1. 确定本地根目录
            String localRootPath = appProperties.getLocalSaveRoot();
            if (localRootPath == null) {
                String os = System.getProperty("os.name").toLowerCase();
                localRootPath = os.contains("win") ? "D:/AiResult" : "/tmp/ai-result";
            }
            final String localRoot = localRootPath; // 确保是 final

            // 2. 智能判断后缀名
            String ext = ".png";
            String lowerUrl = resultUrl.toLowerCase();
            if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) ext = ".jpg";
            else if (lowerUrl.contains(".mp4")) ext = ".mp4";
            else if (lowerUrl.contains(".mov")) ext = ".mov";
            final String extension = ext;

            // 3. 处理前缀并构造最终文件名 (原名_ID.后缀)
            String pref = (spu != null) ? spu : "task";
            if (resultUrl.contains("/")) {
                try {
                    String lastPart = resultUrl.substring(resultUrl.lastIndexOf("/") + 1);
                    // 尝试解码并切掉原有的时间戳/数字尾巴
                    String decoded = java.net.URLDecoder.decode(lastPart, "UTF-8");
                    pref = decoded.replaceAll("_[\\d_]+\\.[a-zA-Z]+$", "");
                } catch (Exception e) {
                    log.warn("文件名解析失败，回退至SPU前缀");
                }
            }
            final String fileName = pref + "_" + taskId + extension;

            // 4. 准备本地文件对象
            File targetDir = new File(localRoot + "/" + spu);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            final File permanentFile = new File(targetDir, fileName);

            // 5. 定义隔离任务 (使用 final 变量)
            Callable<String> isolationTask = () -> {
                log.info("【双保险】正在下载到本地: {}", permanentFile.getAbsolutePath());

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(resultUrl)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build();

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new RuntimeException("KIE文件下载失败: HTTP " + response.code());
                    }

                    // 第一步：网络字节流直接落地本地硬盘
                    try (InputStream in = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(permanentFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception streamEx) {
                        if (permanentFile.exists()) permanentFile.delete();
                        throw new RuntimeException("网络流中断，下载不完整已清理", streamEx);
                    }
                }

                if (!permanentFile.exists() || permanentFile.length() == 0) {
                    throw new RuntimeException("本地保存失败，文件为空");
                }

                log.info("【本地保存成功】准备上传 OSS...");

                String ossUrl = "";
                try {
                    String objectName = spu + "/result/" + fileName;

                    // 🔴 关键优化：根据后缀设置 Metadata，确保视频可在线播放
                    com.aliyun.oss.model.ObjectMetadata metadata = new com.aliyun.oss.model.ObjectMetadata();
                    if (extension.equals(".jpg")) metadata.setContentType("image/jpeg");
                    else if (extension.equals(".png")) metadata.setContentType("image/png");
                    else if (extension.equals(".mp4")) metadata.setContentType("video/mp4");
                    else if (extension.equals(".mov")) metadata.setContentType("video/quicktime");

                    ossClient.putObject(oss.getResultBucket(), objectName, permanentFile, metadata);
                    ossUrl = oss.getResultPublicHost() + "/" + objectName;
                    log.info("【OSS上传成功】链接: {}", ossUrl);
                } catch (Exception ossEx) {
                    log.error("【OSS上传失败】本地已保留原图: {}", ossEx.getMessage());
                }

                // 6. 环境清理逻辑
                String finalPathStatus = permanentFile.getAbsolutePath();
                if (appProperties.isDeleteLocalAfterUpload() && !ossUrl.isEmpty()) {
                    permanentFile.delete();
                    finalPathStatus = "DELETED";
                }

                return ossUrl + "|" + finalPathStatus;
            };

            // 7. 提交并设置总超时
            future = isolationPool.submit(isolationTask);
            return future.get(5410, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            throw new RuntimeException("任务超时1.5小时被强制终止");
        } catch (Exception e) {
            log.error("【转存异常】: {}", e.getMessage());
            throw new RuntimeException("系统转存失败", e);
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