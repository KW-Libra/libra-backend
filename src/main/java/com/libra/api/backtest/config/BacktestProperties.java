package com.libra.api.backtest.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.backtests")
public record BacktestProperties(Path outputRoot) {

    public BacktestProperties {
        if (outputRoot == null) {
            outputRoot = Path.of("/opt/libra/backtests");
        }
    }
}
