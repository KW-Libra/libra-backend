package com.libra.api.ingest.service;

import com.libra.api.agent.api.dto.RunStartRequest;
import com.libra.api.auth.domain.User;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.ingest.config.LiveIngestProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class LiveIngestService {

    private final LiveIngestProperties props;
    private final ObjectMapper objectMapper;

    public LiveIngestService(LiveIngestProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public RunStartRequest prepare(RunStartRequest req, User user, String traceId) {
        if (req.ingest_bundle() != null && !req.ingest_bundle().isEmpty()) {
            return req.withIngestBundle(req.ingest_bundle());
        }
        if (!hasHoldings(req.portfolio())) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "실서비스 run에는 holdings가 포함된 portfolio가 필요합니다. 잔고 동기화 후 다시 실행하세요."
            );
        }

        Path ingestRoot = props.workspaceRoot().toAbsolutePath().normalize();
        if (!Files.isDirectory(ingestRoot.resolve("src").resolve("libra_ingest"))) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "libra-ingest repo를 찾을 수 없습니다: " + ingestRoot);
        }

        String safeTraceId = sanitize(traceId);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(OffsetDateTime.now());
        Path runDir = props.outputRoot().toAbsolutePath().normalize().resolve(timestamp + "-" + safeTraceId);
        Path portfolioPath = runDir.resolve("portfolio.json");
        Path bundlePath = runDir.resolve("ingest-bundle.json");
        Path logPath = runDir.resolve("backend-live-ingest.log");

        try {
            Files.createDirectories(runDir);
            Files.writeString(portfolioPath, objectMapper.writeValueAsString(req.portfolio()), StandardCharsets.UTF_8);
            runBuilder(runDir, logPath);
            Map<String, Object> bundle = buildBundle(runDir, user, safeTraceId);
            Files.writeString(bundlePath, objectMapper.writeValueAsString(bundle), StandardCharsets.UTF_8);
            return req.withIngestBundle(bundle);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "실서비스 ingest bundle 생성에 실패했습니다", e);
        }
    }

    private void runBuilder(
        Path runDir,
        Path logPath
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(props.pythonCommand());
        command.add("-m");
        command.add("libra_ingest.ingest_cli");
        command.add("--live-baseline");
        command.add("--out-dir");
        command.add(runDir.toString());
        command.add("--emit-push-candidates");
        command.add("--pretty");
        command.add("--require-article-body");
        command.add("--rss-limit");
        command.add(Integer.toString(props.rssLimit()));
        command.add("--dart-limit");
        command.add(Integer.toString(props.dartLimit()));
        command.add("--report-limit");
        command.add(Integer.toString(props.reportLimit()));
        command.add("--report-pdf-pages");
        command.add(Integer.toString(props.reportPdfPages()));
        command.add("--report-min-body-chars");
        command.add(Integer.toString(props.reportMinBodyChars()));

        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(runDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        builder.environment().put("PYTHONPATH", props.workspaceRoot().toAbsolutePath().normalize().resolve("src").toString());
        Process process = builder.start();
        boolean completed = process.waitFor(props.timeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new ApiException(
                ErrorCode.AGENT_TIMEOUT,
                "실서비스 ingest bundle 생성이 제한 시간 안에 끝나지 않았습니다. log=" + logPath
            );
        }
        if (process.exitValue() != 0) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "실서비스 ingest bundle 생성 실패. log tail=" + tail(logPath, 3000)
            );
        }
        if (!Files.isRegularFile(runDir.resolve("events.json"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "libra-ingest events.json이 생성되지 않았습니다: " + runDir);
        }
        if (!Files.isRegularFile(runDir.resolve("normalized_documents.json"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "libra-ingest normalized_documents.json이 생성되지 않았습니다: " + runDir);
        }
    }

    private Map<String, Object> buildBundle(Path runDir, User user, String traceId) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> eventsPayload = objectMapper.readValue(
            Files.readString(runDir.resolve("events.json"), StandardCharsets.UTF_8),
            Map.class
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> documentsPayload = objectMapper.readValue(
            Files.readString(runDir.resolve("normalized_documents.json"), StandardCharsets.UTF_8),
            Map.class
        );
        List<?> events = eventsPayload.get("events") instanceof List<?> list ? list : List.of();
        List<?> documents = documentsPayload.get("documents") instanceof List<?> list ? list : List.of();
        if (events.isEmpty() && documents.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "libra-ingest가 사용 가능한 문서/이벤트를 생성하지 못했습니다");
        }
        String asOf = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("bundle_id", "service-run-" + traceId);
        bundle.put("as_of", asOf);
        bundle.put("portfolio_id", user.getId().toString());
        bundle.put("source_policy", "libra-ingest live baseline: RSS article body required + DART + reports");
        bundle.put("prices_until", LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
        bundle.put("observed_count", events.size());
        bundle.put("portfolio_relevant_count", events.size());
        bundle.put("usable_for_trade_decision", true);
        bundle.put("items", events);
        bundle.put("document_count", documents.size());
        bundle.put("documents", documents);
        return bundle;
    }

    private static boolean hasHoldings(Map<String, Object> portfolio) {
        if (portfolio == null) {
            return false;
        }
        Object holdings = portfolio.get("holdings");
        return holdings instanceof List<?> list && !list.isEmpty();
    }

    private static String sanitize(String value) {
        String input = value == null || value.isBlank() ? "missing-trace-id" : value;
        String sanitized = input.replaceAll("[^A-Za-z0-9_.-]", "-");
        if (sanitized.length() > 80) {
            return sanitized.substring(0, 80);
        }
        return sanitized;
    }

    private static String tail(Path path, int maxChars) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.length() <= maxChars) {
                return text;
            }
            return text.substring(text.length() - maxChars);
        } catch (IOException e) {
            return "unreadable log: " + path;
        }
    }
}
