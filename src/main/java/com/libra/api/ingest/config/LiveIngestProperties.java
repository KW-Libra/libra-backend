package com.libra.api.ingest.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
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
            workspaceRoot = Path.of("/opt/libra/ingest/app");
        }
        if (outputRoot == null) {
            outputRoot = Path.of("/opt/libra/backend/live-ingest");
        }
        pythonCommand = resolvePythonCommand(workspaceRoot, pythonCommand);
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

    private static String resolvePythonCommand(Path workspaceRoot, String configured) {
        Path workspaceVenv = workspaceRoot
            .resolve(".venv")
            .resolve(isWindows() ? "Scripts/python.exe" : "bin/python");
        if (Files.isRegularFile(workspaceVenv) && shouldPreferWorkspaceVenv(configured)) {
            return workspaceVenv.toString();
        }
        if (configured == null || configured.isBlank()) {
            return isWindows() ? "python" : "/opt/libra/ingest/.venv/bin/python";
        }
        return configured;
    }

    private static boolean shouldPreferWorkspaceVenv(String configured) {
        if (configured == null || configured.isBlank()) {
            return true;
        }
        String normalized = configured.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.equals("python") || normalized.equals("python.exe")) {
            return true;
        }
        try {
            return !Files.exists(Path.of(configured));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
