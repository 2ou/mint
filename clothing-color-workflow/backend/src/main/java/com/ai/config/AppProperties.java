package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private long waitResultMs;

    // 🔴 必须确保有这两个字段，Spring 才能把 yml 里的值注入进来
    private String localSaveRoot;
    private boolean deleteLocalAfterUpload = true;

    // 🔴 关键修复：必须 new 一下，不能只是 private Kie kie;
    private Kie kie = new Kie();

    // 如果有 oss，同理也 new 一下
    private Oss oss = new Oss();

    public String getLocalSaveRoot() { return localSaveRoot; }
    public void setLocalSaveRoot(String localSaveRoot) { this.localSaveRoot = localSaveRoot; }

    public long getWaitResultMs() { return waitResultMs; }
    public void setWaitResultMs(long waitResultMs) { this.waitResultMs = waitResultMs; }

    public Oss getOss() { return oss; }
    public void setOss(Oss oss) { this.oss = oss; }

    public Kie getKie() { return kie; }
    public void setKie(Kie kie) { this.kie = kie; }

    public static class Oss {
        private String endpoint;
        private String region;
        private String accessKeyId;
        private String accessKeySecret;
        private String inputBucket;
        private String resultBucket;
        private String inputPublicHost;
        private String resultPublicHost;

        // getters and setters
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getInputBucket() { return inputBucket; }
        public void setInputBucket(String inputBucket) { this.inputBucket = inputBucket; }
        public String getResultBucket() { return resultBucket; }
        public void setResultBucket(String resultBucket) { this.resultBucket = resultBucket; }
        public String getInputPublicHost() { return inputPublicHost; }
        public void setInputPublicHost(String inputPublicHost) { this.inputPublicHost = inputPublicHost; }
        public String getResultPublicHost() { return resultPublicHost; }
        public void setResultPublicHost(String resultPublicHost) { this.resultPublicHost = resultPublicHost; }
    }

    public static class Kie {
        private String baseUrl;
        private String apiKey;
        private String model;

        // getters and setters
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}