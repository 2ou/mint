package com.ai.creative.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "creative")
public class CreativeProperties {
    private Oss oss = new Oss();
    private Kie kie = new Kie();

    @Data
    public static class Oss {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
    }

    @Data
    public static class Kie {
        private String baseUrl;
        private String apiKey;
        private String klingVideoModel;
    }
}
