package com.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    private String endpoint;
    private String inputBucket;
    private String resultBucket;
    private String accessKeyId;
    private String accessKeySecret;

    // getter & setter
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getInputBucket() { return inputBucket; }
    public void setInputBucket(String inputBucket) { this.inputBucket = inputBucket; }

    public String getResultBucket() { return resultBucket; }
    public void setResultBucket(String resultBucket) { this.resultBucket = resultBucket; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
}