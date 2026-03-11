package com.ai.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CommonConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(AppProperties appProperties) {
        AppProperties.Oss oss = appProperties.getOss();
        return new OSSClientBuilder().build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
    }
}
