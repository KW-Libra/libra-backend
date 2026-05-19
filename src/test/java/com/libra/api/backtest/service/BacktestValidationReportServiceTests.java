package com.libra.api.backtest.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.libra.api.backtest.config.BacktestReportProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BacktestValidationReportServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void readsValidationReportFromFileUri() throws Exception {
        Path reportPath = tempDir.resolve("validation.json");
        Files.writeString(reportPath, "{\"period\":\"2023.05.17 - 2026.05.15\",\"replayDays\":729}");

        BacktestValidationReportService service = new BacktestValidationReportService(
            new BacktestReportProperties(reportPath.toUri(), null, Duration.ofMinutes(5)),
            new ObjectMapper(),
            null,
            Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneOffset.UTC)
        );

        JsonNode report = service.publicRss3yValidation();

        assertEquals("2023.05.17 - 2026.05.15", report.get("period").asString());
        assertEquals(729, report.get("replayDays").asInt());
    }
}
