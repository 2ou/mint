package com.ai.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class CommonConfig {

    private final AppProperties appProperties;

    public CommonConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public OSS ossClient() {
        AppProperties.Oss oss = appProperties.getOss();
        if (oss == null) {
            throw new RuntimeException("OSS 配置未注入，请检查 application.yml");
        }

        return new OSSClientBuilder().build(
                oss.getEndpoint(),
                oss.getAccessKeyId(),
                oss.getAccessKeySecret()
        );
    }

    /**
     * A+ 模块专用线程池，用于异步调用外部 API（LLM、KIE）
     * 避免占用 ForkJoinPool.commonPool 导致线程饥饿
     */
    @Bean(name = "aplusAsyncExecutor")
    public Executor aplusAsyncExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}