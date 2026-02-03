package com.mint.batch.config;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AppConfig {
    private final List<String> arrayA;
    private final List<String> arrayB;
    private final String promptTemplate;
    private final String kieEndpoint;
    private final String kieApiKey;
    private final String kieModelId;
    private final Path outputDir;
    private final String ossEndpoint;
    private final String ossAccessKeyId;
    private final String ossAccessKeySecret;
    private final String ossBucket;
    private final String ossBasePath;

    public AppConfig(List<String> arrayA,
                     List<String> arrayB,
                     String promptTemplate,
                     String kieEndpoint,
                     String kieApiKey,
                     String kieModelId,
                     Path outputDir,
                     String ossEndpoint,
                     String ossAccessKeyId,
                     String ossAccessKeySecret,
                     String ossBucket,
                     String ossBasePath) {
        this.arrayA = arrayA;
        this.arrayB = arrayB;
        this.promptTemplate = promptTemplate;
        this.kieEndpoint = kieEndpoint;
        this.kieApiKey = kieApiKey;
        this.kieModelId = kieModelId;
        this.outputDir = outputDir;
        this.ossEndpoint = ossEndpoint;
        this.ossAccessKeyId = ossAccessKeyId;
        this.ossAccessKeySecret = ossAccessKeySecret;
        this.ossBucket = ossBucket;
        this.ossBasePath = ossBasePath;
    }

    public List<String> getArrayA() {
        return arrayA;
    }

    public List<String> getArrayB() {
        return arrayB;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public String getKieEndpoint() {
        return kieEndpoint;
    }

    public String getKieApiKey() {
        return kieApiKey;
    }

    public String getKieModelId() {
        return kieModelId;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public String getOssEndpoint() {
        return ossEndpoint;
    }

    public String getOssAccessKeyId() {
        return ossAccessKeyId;
    }

    public String getOssAccessKeySecret() {
        return ossAccessKeySecret;
    }

    public String getOssBucket() {
        return ossBucket;
    }

    public String getOssBasePath() {
        return ossBasePath;
    }

    public static AppConfig fromEnv() {
        List<String> arrayA = parseList(envOrDefault("ARRAY_A", "猫,狗,鲸鱼"));
        List<String> arrayB = parseList(envOrDefault("ARRAY_B", "油画风,赛博朋克,像素风"));
        String promptTemplate = envOrDefault("PROMPT_TEMPLATE", "以%s和%s为主题，生成一张高清图片");
        String kieEndpoint = requireEnv("KIE_ENDPOINT");
        String kieApiKey = requireEnv("KIE_API_KEY");
        String kieModelId = requireEnv("KIE_MODEL_ID");
        Path outputDir = Path.of(envOrDefault("OUTPUT_DIR", "output"));
        String ossEndpoint = requireEnv("OSS_ENDPOINT");
        String ossAccessKeyId = requireEnv("OSS_ACCESS_KEY_ID");
        String ossAccessKeySecret = requireEnv("OSS_ACCESS_KEY_SECRET");
        String ossBucket = requireEnv("OSS_BUCKET");
        String ossBasePath = envOrDefault("OSS_BASE_PATH", "kie-images");
        return new AppConfig(arrayA, arrayB, promptTemplate, kieEndpoint, kieApiKey, kieModelId,
                outputDir, ossEndpoint, ossAccessKeyId, ossAccessKeySecret, ossBucket, ossBasePath);
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Missing required env: %s", key));
        }
        return value.trim();
    }

    private static List<String> parseList(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }
}
