package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.service.OssService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Paths;

@Service
public class OssServiceImpl implements OssService {

    private final AppProperties appProperties;
    private final OSS ossClient;

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
        try {
            AppProperties.Oss oss = appProperties.getOss();
            URL remoteUrl = new URL(resultUrl);
            String objectName = spu + "/result/" + Paths.get(remoteUrl.getPath()).getFileName();
            ossClient.putObject(oss.getResultBucket(), objectName, remoteUrl.openStream());
            return oss.getResultPublicHost() + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("上传结果文件失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String saveResultToLocal(String spu, String resultUrl, String localRoot) {
        try {
            URL remoteUrl = new URL(resultUrl);
            File dir = new File(localRoot, spu);
            if (!dir.exists()) dir.mkdirs();
            File localFile = new File(dir, Paths.get(remoteUrl.getPath()).getFileName().toString());
            try (var in = remoteUrl.openStream(); var out = new FileOutputStream(localFile)) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
            }
            return localFile.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("保存结果到本地失败：" + e.getMessage(), e);
        }
    }
}