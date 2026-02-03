package com.mint.batch.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.mint.batch.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class OssUploader {
    private final AppConfig config;

    public OssUploader(AppConfig config) {
        this.config = config;
    }

    public void upload(Path filePath, String objectKey) throws IOException {
        try (OSS ossClient = new OSSClientBuilder().build(
                config.getOssEndpoint(),
                config.getOssAccessKeyId(),
                config.getOssAccessKeySecret())) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(Files.size(filePath));
            metadata.setContentType("image/png");
            PutObjectRequest request = new PutObjectRequest(
                    config.getOssBucket(),
                    objectKey,
                    filePath.toFile());
            request.setMetadata(metadata);
            ossClient.putObject(request);
        }
    }
}
