package com.libra.api.backtest.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.backtests.public-rss-3y")
public record BacktestReportProperties(
    URI validationReportUri,
    String s3Region,
    Duration cacheTtl
) {
    public BacktestReportProperties {
        if (s3Region == null || s3Region.isBlank()) {
            s3Region = "ap-northeast-2";
        }
        if (cacheTtl == null) {
            cacheTtl = Duration.ofMinutes(5);
        }
    }
}
