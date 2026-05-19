package com.libra.api.backtest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class BacktestStorageConfig {

    @Bean
    S3Client backtestS3Client(BacktestReportProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.s3Region()))
            .build();
    }
}
