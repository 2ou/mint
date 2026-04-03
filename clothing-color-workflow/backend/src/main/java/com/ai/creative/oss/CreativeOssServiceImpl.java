package com.ai.creative.oss;

import com.ai.creative.config.CreativeProperties;
import com.ai.creative.common.CodeGenUtils;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class CreativeOssServiceImpl implements CreativeOssService {
    private final CreativeProperties creativeProperties;

    @Override
    public String uploadInputStream(String objectKey, InputStream inputStream) {
        CreativeProperties.Oss ossCfg = creativeProperties.getOss();
        OSS oss = new OSSClientBuilder().build(ossCfg.getEndpoint(), ossCfg.getAccessKeyId(), ossCfg.getAccessKeySecret());
        try {
            oss.putObject(ossCfg.getBucket(), objectKey, inputStream);
            return "https://" + ossCfg.getBucket() + "." + ossCfg.getEndpoint().replace("https://", "") + "/" + objectKey;
        } finally {
            oss.shutdown();
        }
    }

    @Override
    public String uploadBytes(String objectKey, byte[] bytes) {
        return uploadInputStream(objectKey, new ByteArrayInputStream(bytes));
    }

    @Override
    public String generateObjectKey(String bizType, String fileName) {
        return "creative/" + bizType + "/" + CodeGenUtils.code("obj") + "_" + fileName;
    }
}
