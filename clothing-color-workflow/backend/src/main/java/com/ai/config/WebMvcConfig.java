package com.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.ai.config.AppProperties;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    // 注入你写的拦截器
    private final TokenInterceptor tokenInterceptor;
    private final AppProperties appProperties;
    private final Environment environment;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/**") // 🔴 拦截所有以 /api 开头的请求
                .excludePathPatterns("/api/auth/login") // 排除登录接口
                .excludePathPatterns("/api/aplus/callback") // KIE 任务回调不带系统登录态
                .excludePathPatterns("/api/canvas/callback") // AI 画布独立回调入口
                .excludePathPatterns("/api/tasks/proxy-download"); // 排除下载接口
    }

    // 🔴 AI 画布结果本地落盘静态服务：仅 dev(本地)环境注册 /ai-result/**
    // prod 结果图走阿里 OSS（delete-local-after-upload=true 已删除本地），不依赖本地路径，故不暴露本地目录
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
        }
        String root = appProperties.getLocalSaveRoot();
        if (root == null) {
            String os = System.getProperty("os.name").toLowerCase();
            root = os.contains("win") ? "D:/AiResult" : "/tmp/ai-result";
        }
        registry.addResourceHandler("/ai-result/**")
                .addResourceLocations("file:" + root + "/")
                // Generated file names are immutable, so repeat LAN previews
                // can stay in the browser cache without hitting the app.
                .setCachePeriod(7 * 24 * 60 * 60);
    }

    // 🔴 终极跨域解决方案：使用 CorsFilter 替代原本的 addCorsMappings
    // 它的执行时机在拦截器之前，能彻底解决“拦截器报错导致跨域头丢失”的难题
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*"); // 允许所有前端来源跨域
        config.addAllowedMethod("*");        // 允许所有请求方法 (GET, POST, OPTIONS 等)
        config.addAllowedHeader("*");        // 允许所有请求头携带 (包括你的 X-User-Token)
        config.setAllowCredentials(true);    // 允许跨域携带认证信息

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
