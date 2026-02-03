package com.mint.batch;

import com.mint.batch.config.AppConfig;
import com.mint.batch.kie.KieClient;
import com.mint.batch.oss.OssUploader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchImageGenerator {
    public static void main(String[] args) throws IOException, InterruptedException {
        AppConfig config = AppConfig.fromEnv();
        Files.createDirectories(config.getOutputDir());

        KieClient kieClient = new KieClient(config);
        OssUploader ossUploader = new OssUploader(config);
        AtomicInteger counter = new AtomicInteger(1);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());

        for (String itemA : config.getArrayA()) {
            for (String itemB : config.getArrayB()) {
                String prompt = String.format(Locale.ROOT, config.getPromptTemplate(), itemA, itemB);
                byte[] imageBytes = kieClient.generateImage(prompt);
                String fileName = String.format(Locale.ROOT, "kie-%s-%03d.png", timestamp, counter.getAndIncrement());
                Path outputPath = config.getOutputDir().resolve(fileName);
                Files.write(outputPath, imageBytes);

                String objectKey = String.format(Locale.ROOT, "%s/%s", config.getOssBasePath(), fileName);
                ossUploader.upload(outputPath, objectKey);

                System.out.printf(Locale.ROOT,
                        "Generated prompt: %s%nSaved locally: %s%nUploaded to OSS: %s/%s%n%n",
                        prompt,
                        outputPath.toAbsolutePath(),
                        config.getOssBucket(),
                        objectKey);
            }
        }
    }
}
