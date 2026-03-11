package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String localSaveRoot;
    private Oss oss;
    private Kie kie;

    @Data
    public static class Oss {
        private String endpoint;
        private String region;
        private String accessKeyId;
        private String accessKeySecret;
        private String inputBucket;
        private String resultBucket;
        private String inputPublicHost;
        private String resultPublicHost;
    }

    @Data
    public static class Kie {
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}
