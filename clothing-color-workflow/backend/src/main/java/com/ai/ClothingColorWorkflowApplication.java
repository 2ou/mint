package com.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableScheduling
@MapperScan({"com.ai.creative.**.mapper"})
public class ClothingColorWorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClothingColorWorkflowApplication.class, args);
    }
}
