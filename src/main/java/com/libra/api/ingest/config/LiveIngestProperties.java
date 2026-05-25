package com.libra.api.ingest.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.ingest")
public record LiveIngestProperties(
    Path workspaceRoot,
    Path outputRoot,
    String pythonCommand,
    int rssLimit,
    int dartLimit,
    int reportLimit,
    int reportPdfPages,
    int reportMinBodyChars,
    Duration timeout
) {

    public LiveIngestProperties {
        if (workspaceRoot == null) {
            workspaceRoot = Path.of("../libra-ingest");
        }
        if (outputRoot == null) {
            outputRoot = Path.of("../libra-ingest/outputs/service-live");
        }
        if (pythonCommand == null || pythonCommand.isBlank()) {
            pythonCommand = "python";
        }
        if (rssLimit <= 0) {
            rssLimit = 20;
        }
        if (dartLimit <= 0) {
            dartLimit = 100;
        }
        if (reportLimit <= 0) {
            reportLimit = 20;
        }
        if (reportPdfPages <= 0) {
            reportPdfPages = 20;
        }
        if (reportMinBodyChars <= 0) {
            reportMinBodyChars = 500;
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofMinutes(15);
        }
    }
}
