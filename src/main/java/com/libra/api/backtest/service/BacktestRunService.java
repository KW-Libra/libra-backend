package com.libra.api.backtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.libra.api.backtest.api.dto.BacktestRunConversationResponse;
import com.libra.api.backtest.api.dto.BacktestRunStartRequest;
import com.libra.api.backtest.api.dto.BacktestRunStatusResponse;
import com.libra.api.backtest.config.BacktestRunnerProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BacktestRunService {

    private static final Set<String> DECISION_FREQUENCIES = Set.of("daily", "every-n-trading-days", "weekly");
    private static final Pattern SLUG_UNSAFE = Pattern.compile("[^a-z0-9]+");
    private static final DateTimeFormatter RUN_TS =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Seoul"));

    private final BacktestRunnerProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BacktestRunService(BacktestRunnerProperties properties) {
        this.properties = properties;
    }

    public synchronized BacktestRunStatusResponse start(BacktestRunStartRequest request) {
        ensureRunnerConfigured();
        validateRequest(request);

        Path agentRoot = properties.agentRepoRoot().normalize();
        Path script = agentRoot.resolve("scripts").resolve("replay_full_committee_backtest.py").normalize();
        Path outputDir = properties.outputDir().normalize();
        Path envFile = properties.envFile().normalize();
        Path bundlesDir = outputDir.resolve("ingest-bundles-article").normalize();
        Path fixture = outputDir.resolve("comparison-fixture.json").normalize();
        String model = valueOrDefault(request.model(), properties.defaultModel());
        String governancePreset = valueOrDefault(request.governancePreset(), properties.defaultGovernancePreset());
        String executionPolicyMode = valueOrDefault(request.executionPolicyMode(), properties.defaultExecutionPolicyMode());
        String frequency = normalizedFrequency(request.decisionFrequency());
        int interval = request.decisionInterval() == null ? 1 : request.decisionInterval();
        String runId = valueOrDefault(request.runId(), generatedRunId(model, governancePreset, frequency, interval));
        boolean force = Boolean.TRUE.equals(request.force());

        if (!Files.isDirectory(agentRoot)) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "libra-agent 경로를 찾을 수 없습니다: " + agentRoot);
        }
        if (!Files.isRegularFile(script)) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "백테스트 replay 스크립트를 찾을 수 없습니다: " + script);
        }
        if (!Files.isRegularFile(envFile)) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "Anthropic API 키 env 파일을 찾을 수 없습니다: " + envFile);
        }
        if (!Files.isRegularFile(fixture)) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "comparison-fixture.json을 찾을 수 없습니다: " + outputDir);
        }
        if (!Files.isRegularFile(bundlesDir.resolve("index.json"))) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "ingest-bundles-article/index.json을 찾을 수 없습니다: " + bundlesDir);
        }
        ActiveRun activeRun = findActiveRun(outputDir);
        if (activeRun != null) {
            throw new ApiException(
                ErrorCode.BACKTEST_RUNNER_CONFLICT,
                "이미 다른 백테스트가 실행 중입니다: " + activeRun.runId() + " (pid=" + activeRun.pid() + ")"
            );
        }

        Path rawOut = outputDir.resolve("libra-replay-results." + runId + ".jsonl").normalize();
        Path decisionsOut = outputDir.resolve("libra-decisions." + runId + ".json").normalize();
        Path summaryOut = outputDir.resolve(runId + ".summary.json").normalize();
        Path usageLog = outputDir.resolve("anthropic-" + runId + ".usage.jsonl").normalize();
        Path traceOut = outputDir.resolve(runId + ".trace.jsonl").normalize();
        Path stdoutLog = outputDir.resolve(runId + ".stdout.log").normalize();
        Path stderrLog = outputDir.resolve(runId + ".stderr.log").normalize();
        Path pidPath = outputDir.resolve(runId + ".pid.json").normalize();
        List<Path> outputPaths = List.of(rawOut, decisionsOut, summaryOut, usageLog, traceOut, stdoutLog, stderrLog, pidPath);
        List<Path> existing = outputPaths.stream().filter(Files::exists).toList();
        if (!existing.isEmpty() && !force) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_CONFLICT, "이미 같은 runId 산출물이 있습니다: " + runId);
        }
        if (force) {
            for (Path path : existing) {
                deleteIfExists(path);
            }
        }

        List<String> command = new ArrayList<>();
        command.add(resolvePythonCommand(agentRoot));
        command.add(script.toString());
        addArg(command, "--fixture", fixture.toString());
        addArg(command, "--bundles-dir", bundlesDir.toString());
        addArg(command, "--out", decisionsOut.toString());
        addArg(command, "--summary-out", summaryOut.toString());
        addArg(command, "--raw-out", rawOut.toString());
        addArg(command, "--usage-log", usageLog.toString());
        addArg(command, "--trace-out", traceOut.toString());
        addArg(command, "--backend", "anthropic");
        addArg(command, "--anthropic-model", model);
        command.add("--fail-on-fallback-events");
        addArg(command, "--decision-frequency", frequency);
        addArg(command, "--decision-interval", Integer.toString(interval));
        addArg(command, "--progress-every", "10");
        if (request.limit() != null) {
            addArg(command, "--limit", request.limit().toString());
        }
        if (request.startDate() != null) {
            addArg(command, "--start-date", request.startDate().toString());
        }
        if (request.endDate() != null) {
            addArg(command, "--end-date", request.endDate().toString());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(agentRoot.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.to(stdoutLog.toFile()));
        builder.redirectError(ProcessBuilder.Redirect.to(stderrLog.toFile()));
        configureReplayEnvironment(
            builder.environment(),
            envFile,
            model,
            governancePreset,
            request.promptVariant(),
            executionPolicyMode,
            request.executionParticipationRate(),
            request.executionMaxAbsDeltaPct(),
            boolText(request.executionResolveTickerConflicts(), true),
            boolText(request.issueStateEnabled(), true),
            intText(request.issueStateCooldownObservations(), 20)
        );

        try {
            Process process = builder.start();
            writePid(
                pidPath,
                runId,
                process.pid(),
                agentRoot,
                command.getFirst(),
                script,
                fixture,
                bundlesDir,
                rawOut,
                decisionsOut,
                summaryOut,
                usageLog,
                traceOut,
                stdoutLog,
                stderrLog,
                model,
                governancePreset,
                request.promptVariant(),
                executionPolicyMode,
                request.executionParticipationRate(),
                request.executionMaxAbsDeltaPct(),
                boolText(request.executionResolveTickerConflicts(), true),
                boolText(request.issueStateEnabled(), true),
                intText(request.issueStateCooldownObservations(), 20),
                sourceFixtureRows(fixture),
                expectedRows(fixture, request.startDate() == null ? null : request.startDate().toString(), request.endDate() == null ? null : request.endDate().toString(), request.limit()),
                request.limit(),
                request.startDate() == null ? null : request.startDate().toString(),
                request.endDate() == null ? null : request.endDate().toString(),
                frequency,
                interval
            );
            Thread.sleep(500);
            if (!process.isAlive() && process.exitValue() != 0) {
                throw new ApiException(
                    ErrorCode.BACKTEST_RUNNER_FAILED,
                    "백테스트 시작 실패: " + compact(String.join("\n", tail(stderrLog, 20)), String.join("\n", tail(stdoutLog, 20)))
                );
            }
            return status(runId);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "백테스트 시작 프로세스를 실행할 수 없습니다", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "백테스트 시작 대기 중 인터럽트되었습니다", e);
        }
    }

    public BacktestRunStatusResponse status(String runId) {
        ensureRunnerConfigured();
        if (runId == null || runId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "runId가 필요합니다");
        }
        Path outputDir = properties.outputDir().normalize();
        Path pidPath = outputDir.resolve(runId + ".pid.json");
        if (!Files.isRegularFile(pidPath)) {
            return missingStatus(runId, outputDir);
        }

        JsonNode pid = readJson(pidPath);
        Path rawOut = path(pid, "raw_out", outputDir.resolve("libra-replay-results." + runId + ".jsonl"));
        Path usageLog = path(pid, "usage_log", outputDir.resolve("anthropic-" + runId + ".usage.jsonl"));
        Path traceOut = path(pid, "trace_out", outputDir.resolve(runId + ".trace.jsonl"));
        Path stdoutLog = path(pid, "stdout_log", outputDir.resolve(runId + ".stdout.log"));
        Path stderrLog = path(pid, "stderr_log", outputDir.resolve(runId + ".stderr.log"));
        Path fixture = path(pid, "fixture", outputDir.resolve("comparison-fixture.json"));

        RawStats rawStats = readRawStats(rawOut, fixture);
        UsageStats usageStats = readUsageStats(usageLog);
        List<String> stdoutTail = tail(stdoutLog, properties.statusTailLines());
        List<String> stderrTail = tail(stderrLog, properties.statusTailLines());
        long pidValue = longValue(pid, "pid");
        boolean running = isProcessRunning(pidValue);
        String status = running ? "RUNNING" : "EXITED";

        return new BacktestRunStatusResponse(
            text(pid, "run_id", runId),
            status,
            pidValue > 0 ? pidValue : null,
            text(pid, "started_at", null),
            text(pid, "model", null),
            text(pid, "governance_preset", null),
            text(pid, "execution_policy_mode", null),
            bool(pid, "issue_state_enabled"),
            bool(pid, "execution_resolve_ticker_conflicts"),
            text(pid, "decision_frequency", "daily"),
            integer(pid, "decision_interval"),
            text(pid, "start_date", null),
            text(pid, "end_date", null),
            integer(pid, "expected_rows"),
            rawStats.rawRows(),
            rawStats.lastDate(),
            rawStats.prefixMatch(),
            rawStats.decisions(),
            rawStats.branches(),
            rawStats.nonemptyRebalanceCount(),
            rawStats.emptyRebalanceCount(),
            rawStats.userDecisionRequiredCount(),
            rawStats.issueStateSuppressionCount(),
            rawStats.round1Agents().size(),
            new ArrayList<>(rawStats.round1Agents()),
            countMatchingLines(traceOut, "fallback"),
            usageStats.requests(),
            usageStats.inputTokens(),
            usageStats.outputTokens(),
            usageStats.cacheCreationInputTokens(),
            usageStats.cacheReadInputTokens(),
            modifiedAt(rawOut),
            eta(pid, rawStats.rawRows()),
            rawOut.toString(),
            usageLog.toString(),
            traceOut.toString(),
            stdoutLog.toString(),
            stderrLog.toString(),
            stdoutTail,
            stderrTail
        );
    }

    public BacktestRunConversationResponse conversation(String runId, String date) {
        ensureRunnerConfigured();
        if (runId == null || runId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "runId가 필요합니다");
        }
        Path outputDir = properties.outputDir().normalize();
        Path pidPath = outputDir.resolve(runId + ".pid.json");
        Path rawOut = Files.isRegularFile(pidPath)
            ? path(readJson(pidPath), "raw_out", outputDir.resolve("libra-replay-results." + runId + ".jsonl"))
            : outputDir.resolve("libra-replay-results." + runId + ".jsonl");

        List<JsonNode> rows = rawRows(rawOut);
        List<BacktestRunConversationResponse.BacktestRunDaySummary> days = new ArrayList<>();
        JsonNode selected = null;
        String requestedDate = date == null || date.isBlank() ? null : date.trim();

        for (JsonNode row : rows) {
            String rowDate = text(row, "date", null);
            if (rowDate == null) {
                continue;
            }
            days.add(daySummary(row));
            if ((requestedDate != null && requestedDate.equals(rowDate)) || (requestedDate == null)) {
                selected = row;
            }
        }

        if (requestedDate != null && selected == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "해당 날짜의 백테스트 대화를 찾을 수 없습니다: " + requestedDate);
        }

        return new BacktestRunConversationResponse(
            runId,
            selected == null ? null : text(selected, "date", null),
            days,
            selected == null ? null : dayConversation(selected)
        );
    }

    private void ensureRunnerConfigured() {
        if (!properties.enabled()) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_DISABLED);
        }
    }

    private static void validateRequest(BacktestRunStartRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.startDate().isAfter(request.endDate())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "startDate는 endDate보다 늦을 수 없습니다");
        }
        String frequency = normalizedFrequency(request.decisionFrequency());
        if (!DECISION_FREQUENCIES.contains(frequency)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 판단 주기입니다: " + request.decisionFrequency());
        }
        if ("every-n-trading-days".equals(frequency) && (request.decisionInterval() == null || request.decisionInterval() < 2)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "N거래일 주기는 decisionInterval이 2 이상이어야 합니다");
        }
    }

    private static String normalizedFrequency(String value) {
        if (value == null || value.isBlank()) {
            return "daily";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String generatedRunId(String model, String preset, String frequency, int interval) {
        String frequencySlug = "every-n-trading-days".equals(frequency) ? "every-" + interval + "td" : frequency;
        return "article-" + slug(model) + "-" + slug(preset) + "-admin-" + frequencySlug + "-" + RUN_TS.format(Instant.now());
    }

    private static String slug(String value) {
        return SLUG_UNSAFE.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("(^-|-$)", "");
    }

    private static void addArg(List<String> command, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        command.add(name);
        command.add(value);
    }

    private String resolvePythonCommand(Path agentRoot) {
        if (properties.pythonCommand() != null && !properties.pythonCommand().isBlank()) {
            return properties.pythonCommand();
        }
        Path windowsVenv = agentRoot.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.isRegularFile(windowsVenv)) {
            return windowsVenv.toString();
        }
        Path posixVenv = agentRoot.resolve(".venv").resolve("bin").resolve("python");
        if (Files.isRegularFile(posixVenv)) {
            return posixVenv.toString();
        }
        return isWindows() ? "python" : "python3";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void configureReplayEnvironment(
        Map<String, String> env,
        Path envFile,
        String model,
        String governancePreset,
        String promptVariant,
        String executionPolicyMode,
        String executionParticipationRate,
        String executionMaxAbsDeltaPct,
        String executionResolveTickerConflicts,
        String issueStateEnabled,
        String issueStateCooldownObservations
    ) {
        loadDotenv(env, envFile);
        if (isBlank(env.get("ANTHROPIC_API_KEY"))) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "ANTHROPIC_API_KEY가 env 파일에 필요합니다: " + envFile);
        }
        env.put("PYTHONPATH", "src");
        env.put("LIBRA_LLM_PROVIDER", "anthropic");
        env.put("LIBRA_ANTHROPIC_MODEL", model);
        env.put("LIBRA_DOMAIN_AGENTS_ENABLED", "true");
        env.put("LLM_ROUTING_POLICY", "claude");
        env.put("LIBRA_DISABLE_AGENT_FALLBACKS", "true");
        env.put("LIBRA_SENTIMENT_PHASE2_ENABLED", "false");
        env.put("LIBRA_LLM_TIMEOUT_SECONDS", "180");
        env.put("LIBRA_LLM_REQUEST_TIMEOUT_SECONDS", "180");
        env.put("LIBRA_ANTHROPIC_CHAT_JSON_ATTEMPTS", valueOrDefault(env.get("LIBRA_ANTHROPIC_CHAT_JSON_ATTEMPTS"), "5"));
        env.put("LIBRA_ANTHROPIC_RETRY_SLEEP_SECONDS", valueOrDefault(env.get("LIBRA_ANTHROPIC_RETRY_SLEEP_SECONDS"), "2"));
        env.put("LIBRA_COMMITTEE_ROUND1_MAX_WORKERS", "11");
        env.put("LIBRA_COMMITTEE_ROUND2_MAX_WORKERS", "4");
        env.put("LIBRA_COMMITTEE_LLM_REPAIR_ATTEMPTS", "1");
        env.put("LIBRA_DROP_INVALID_MEDIATOR_TARGETS", "true");
        env.put("LIBRA_COMMITTEE_OPINION_REASONING_CHARS", "420");
        putOrRemove(env, "LIBRA_GOVERNANCE_PRESET", governancePreset);
        putOrRemove(env, "LIBRA_PROMPT_VARIANT", promptVariant);
        putOrRemove(env, "LIBRA_EXECUTION_POLICY_MODE", executionPolicyMode);
        putOrRemove(env, "LIBRA_EXECUTION_PARTICIPATION_RATE", executionParticipationRate);
        putOrRemove(env, "LIBRA_EXECUTION_MAX_ABS_DELTA_PCT", executionMaxAbsDeltaPct);
        putOrRemove(env, "LIBRA_EXECUTION_RESOLVE_TICKER_CONFLICTS", executionResolveTickerConflicts);
        putOrRemove(env, "LIBRA_ISSUE_STATE_ENABLED", issueStateEnabled);
        putOrRemove(env, "LIBRA_ISSUE_STATE_COOLDOWN_OBSERVATIONS", issueStateCooldownObservations);
    }

    private static void loadDotenv(Map<String, String> env, Path envFile) {
        for (String row : readAllLines(envFile)) {
            String line = row.trim();
            if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            String[] parts = line.split("=", 2);
            String name = parts[0].trim();
            String value = parts.length > 1 ? parts[1].trim() : "";
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!name.isBlank()) {
                env.put(name, value);
            }
        }
    }

    private static List<String> readAllLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private static void putOrRemove(Map<String, String> env, String key, String value) {
        if (value == null || value.isBlank()) {
            env.remove(key);
        } else {
            env.put(key, value);
        }
    }

    private void writePid(
        Path pidPath,
        String runId,
        long pid,
        Path agentRoot,
        String python,
        Path script,
        Path fixture,
        Path bundlesDir,
        Path rawOut,
        Path decisionsOut,
        Path summaryOut,
        Path usageLog,
        Path traceOut,
        Path stdoutLog,
        Path stderrLog,
        String model,
        String governancePreset,
        String promptVariant,
        String executionPolicyMode,
        String executionParticipationRate,
        String executionMaxAbsDeltaPct,
        String executionResolveTickerConflicts,
        String issueStateEnabled,
        String issueStateCooldownObservations,
        int sourceFixtureRows,
        int expectedRows,
        Integer limit,
        String startDate,
        String endDate,
        String frequency,
        int interval
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId);
        payload.put("pid", pid);
        payload.put("started_at", OffsetDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("cwd", agentRoot.toString());
        payload.put("python", python);
        payload.put("script", script.toString());
        payload.put("fixture", fixture.toString());
        payload.put("bundles_dir", bundlesDir.toString());
        payload.put("raw_out", rawOut.toString());
        payload.put("decisions_out", decisionsOut.toString());
        payload.put("summary_out", summaryOut.toString());
        payload.put("usage_log", usageLog.toString());
        payload.put("trace_out", traceOut.toString());
        payload.put("stdout_log", stdoutLog.toString());
        payload.put("stderr_log", stderrLog.toString());
        payload.put("source_fixture_rows", sourceFixtureRows);
        payload.put("expected_rows", expectedRows);
        payload.put("requested_limit", limit == null ? 0 : limit);
        payload.put("start_date", startDate);
        payload.put("end_date", endDate);
        payload.put("decision_frequency", frequency);
        payload.put("decision_interval", interval);
        payload.put("backend", "anthropic");
        payload.put("model", model);
        payload.put("governance_preset", valueOrDefault(governancePreset, "default"));
        payload.put("prompt_variant", valueOrDefault(promptVariant, "default"));
        payload.put("runtime", "JudgeOrchestrator.run_v1_committee");
        payload.put("domain_agents_enabled", "true");
        payload.put("disable_agent_fallbacks", "true");
        payload.put("sentiment_phase2_enabled", "false");
        payload.put("committee_round1_max_workers", "11");
        payload.put("committee_round2_max_workers", "4");
        payload.put("execution_policy_mode", executionPolicyMode);
        payload.put("execution_participation_rate", executionParticipationRate);
        payload.put("execution_max_abs_delta_pct", executionMaxAbsDeltaPct);
        payload.put("execution_resolve_ticker_conflicts", executionResolveTickerConflicts);
        payload.put("issue_state_enabled", issueStateEnabled);
        payload.put("issue_state_cooldown_observations", issueStateCooldownObservations);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(pidPath.toFile(), payload);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "PID 파일을 쓸 수 없습니다: " + pidPath, e);
        }
    }

    private int sourceFixtureRows(Path fixture) {
        return fixtureDates(fixture).size();
    }

    private int expectedRows(Path fixture, String startDate, String endDate, Integer limit) {
        int selected = 0;
        for (String date : fixtureDates(fixture)) {
            if ((startDate == null || date.compareTo(startDate) >= 0) && (endDate == null || date.compareTo(endDate) <= 0)) {
                selected++;
            }
        }
        return limit == null ? selected : Math.min(limit, selected);
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "기존 산출물을 삭제할 수 없습니다: " + path, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String boolText(Boolean value, boolean defaultValue) {
        return Boolean.toString(value == null ? defaultValue : value);
    }

    private static String intText(Integer value, int defaultValue) {
        return Integer.toString(value == null ? defaultValue : value);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String compact(String first, String second) {
        String value = valueOrDefault(first, second).replaceAll("\\s+", " ").trim();
        return value.length() > 700 ? value.substring(0, 700) + "..." : value;
    }

    private BacktestRunStatusResponse missingStatus(String runId, Path outputDir) {
        return new BacktestRunStatusResponse(
            runId,
            "MISSING",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            Map.of(),
            Map.of(),
            0,
            0,
            0,
            0,
            0,
            List.of(),
            0,
            0,
            0L,
            0L,
            0L,
            0L,
            null,
            null,
            outputDir.resolve("libra-replay-results." + runId + ".jsonl").toString(),
            outputDir.resolve("anthropic-" + runId + ".usage.jsonl").toString(),
            outputDir.resolve(runId + ".trace.jsonl").toString(),
            outputDir.resolve(runId + ".stdout.log").toString(),
            outputDir.resolve(runId + ".stderr.log").toString(),
            List.of(),
            List.of()
        );
    }

    private RawStats readRawStats(Path rawOut, Path fixture) {
        List<String> rawLines = lines(rawOut);
        List<String> fixtureDates = fixtureDates(fixture);
        Map<String, Integer> decisions = new LinkedHashMap<>();
        Map<String, Integer> branches = new LinkedHashMap<>();
        Set<String> agents = new LinkedHashSet<>();
        String lastDate = null;
        int nonemptyRebalance = 0;
        int emptyRebalance = 0;
        int handoff = 0;
        int issueSuppressed = 0;
        boolean prefixMatch = true;

        for (int index = 0; index < rawLines.size(); index++) {
            String line = rawLines.get(index);
            JsonNode row;
            try {
                row = objectMapper.readTree(line);
            } catch (IOException e) {
                prefixMatch = false;
                continue;
            }
            String date = text(row, "date", null);
            lastDate = date == null ? lastDate : date;
            if (index < fixtureDates.size() && date != null && !date.equals(fixtureDates.get(index))) {
                prefixMatch = false;
            }

            JsonNode result = row.path("result");
            JsonNode decisionNode = result.path("decision");
            JsonNode finalNode = result.path("governance_v1").path("final_decision");
            String decision = firstText(decisionNode.path("decision"), finalNode.path("decision"));
            if (decision != null) {
                increment(decisions, decision);
            }
            String branch = firstText(finalNode.path("branch"), decisionNode.path("auto_safeguards").path("governance_v1_branch"));
            if (branch != null) {
                increment(branches, branch);
            }
            JsonNode plan = decisionNode.path("candidate_rebalance_plan");
            if ("REBALANCE".equals(decision)) {
                if (isNonEmptyPlan(plan) || isNonEmptyPlan(finalNode.path("trades"))) {
                    nonemptyRebalance++;
                } else {
                    emptyRebalance++;
                }
            }
            if ("USER_DECISION_REQUIRED".equals(decision)
                || boolValue(decisionNode.path("user_notification").path("action_required"))
                || !finalNode.path("user_question").isMissingNode() && !finalNode.path("user_question").isNull()) {
                handoff++;
            }
            if (line.toLowerCase(Locale.ROOT).contains("pending_user_decision_suppressed")
                || line.toLowerCase(Locale.ROOT).contains("suppressed")) {
                issueSuppressed++;
            }
            for (JsonNode agent : result.path("agent_responses")) {
                String agentId = text(agent, "agent_id", null);
                if (agentId != null && !agentId.isBlank()) {
                    agents.add(agentId);
                }
            }
        }
        if (rawLines.size() > fixtureDates.size()) {
            prefixMatch = false;
        }
        return new RawStats(
            rawLines.size(),
            lastDate,
            prefixMatch,
            decisions,
            branches,
            nonemptyRebalance,
            emptyRebalance,
            handoff,
            issueSuppressed,
            agents
        );
    }

    private UsageStats readUsageStats(Path usageLog) {
        long input = 0;
        long output = 0;
        long cacheCreation = 0;
        long cacheRead = 0;
        int requests = 0;
        for (String line : lines(usageLog)) {
            try {
                JsonNode usage = objectMapper.readTree(line).path("usage");
                if (!usage.isMissingNode()) {
                    requests++;
                    input += usage.path("input_tokens").asLong(0);
                    output += usage.path("output_tokens").asLong(0);
                    cacheCreation += usage.path("cache_creation_input_tokens").asLong(0);
                    cacheRead += usage.path("cache_read_input_tokens").asLong(0);
                }
            } catch (IOException ignored) {
                // Ignore partial lines while the replay is still writing.
            }
        }
        return new UsageStats(requests, input, output, cacheCreation, cacheRead);
    }

    private List<JsonNode> rawRows(Path rawOut) {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : lines(rawOut)) {
            try {
                rows.add(objectMapper.readTree(line));
            } catch (IOException ignored) {
                // Ignore a partial trailing line while the replay is still writing.
            }
        }
        return rows;
    }

    private BacktestRunConversationResponse.BacktestRunDaySummary daySummary(JsonNode row) {
        JsonNode result = row.path("result");
        JsonNode decisionNode = result.path("decision");
        JsonNode finalNode = result.path("governance_v1").path("final_decision");
        String decision = firstText(finalNode.path("decision"), decisionNode.path("decision"));
        String branch = firstText(finalNode.path("branch"), decisionNode.path("auto_safeguards").path("governance_v1_branch"));
        JsonNode trades = finalNode.path("trades");
        boolean userActionRequired = "USER_DECISION_REQUIRED".equals(decision)
            || boolValue(decisionNode.path("user_notification").path("action_required"))
            || (!finalNode.path("user_question").isMissingNode() && !finalNode.path("user_question").isNull());

        return new BacktestRunConversationResponse.BacktestRunDaySummary(
            text(row, "date", null),
            decision,
            branch,
            result.path("agent_responses").isArray() ? result.path("agent_responses").size() : 0,
            trades.isArray() ? trades.size() : 0,
            userActionRequired
        );
    }

    private BacktestRunConversationResponse.BacktestRunDayConversation dayConversation(JsonNode row) {
        JsonNode result = row.path("result");
        JsonNode governance = result.path("governance_v1");
        JsonNode finalNode = governance.path("final_decision");
        JsonNode decisionNode = result.path("decision");
        BacktestRunConversationResponse.BacktestRunFinalDecision finalDecision =
            new BacktestRunConversationResponse.BacktestRunFinalDecision(
                firstText(finalNode.path("decision"), decisionNode.path("decision")),
                firstText(finalNode.path("branch"), decisionNode.path("auto_safeguards").path("governance_v1_branch")),
                truncate(text(finalNode, "reasoning", null), 2400),
                truncate(text(finalNode, "user_question", null), 800),
                mapList(finalNode.path("trades"))
            );

        return new BacktestRunConversationResponse.BacktestRunDayConversation(
            text(row, "date", null),
            truncate(text(row, "query", null), 600),
            text(row, "model", null),
            finalDecision,
            agentMessages(result.path("agent_responses")),
            agentMessages(governance.path("round2_responses")),
            mapObject(governance.path("execution_plan"))
        );
    }

    private List<BacktestRunConversationResponse.BacktestRunAgentMessage> agentMessages(JsonNode nodes) {
        if (!nodes.isArray()) {
            return List.of();
        }
        List<BacktestRunConversationResponse.BacktestRunAgentMessage> messages = new ArrayList<>();
        for (JsonNode node : nodes) {
            messages.add(new BacktestRunConversationResponse.BacktestRunAgentMessage(
                text(node, "agent_id", null),
                text(node, "opinion", null),
                text(node, "verdict", null),
                doubleOrNull(node.path("confidence")),
                doubleOrNull(node.path("direction")),
                text(node, "risk_level", null),
                textList(node.path("focus_tickers")),
                truncate(text(node, "query_understood", null), 600),
                truncate(text(node, "reasoning_for_judge_agent", null), 2600),
                truncate(text(node, "limits_acknowledged", null), 1200),
                toolCalls(node.path("tools_called"))
            ));
        }
        return messages;
    }

    private List<BacktestRunConversationResponse.BacktestRunToolCall> toolCalls(JsonNode nodes) {
        if (!nodes.isArray()) {
            return List.of();
        }
        List<BacktestRunConversationResponse.BacktestRunToolCall> calls = new ArrayList<>();
        for (JsonNode node : nodes) {
            calls.add(new BacktestRunConversationResponse.BacktestRunToolCall(
                text(node, "tool_name", null),
                truncate(text(node, "purpose", null), 400),
                truncate(text(node, "summary", null), 900)
            ));
        }
        return calls;
    }

    private List<String> textList(JsonNode nodes) {
        if (!nodes.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (!node.isNull() && !node.asText().isBlank()) {
                values.add(node.asText());
            }
        }
        return values;
    }

    private List<Map<String, Object>> mapList(JsonNode nodes) {
        if (!nodes.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (node.isObject()) {
                values.add(mapObject(node));
            }
        }
        return values;
    }

    private Map<String, Object> mapObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private static Double doubleOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asDouble();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private List<String> fixtureDates(Path fixture) {
        if (!Files.isRegularFile(fixture)) {
            return List.of();
        }
        try {
            List<String> dates = new ArrayList<>();
            JsonNode prices = objectMapper.readTree(fixture.toFile()).path("prices");
            for (JsonNode price : prices) {
                String date = text(price, "date", null);
                if (date != null) {
                    dates.add(date);
                }
            }
            return dates;
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> lines(Path path) {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> tail(Path path, int count) {
        List<String> rows = lines(path);
        if (rows.size() <= count) {
            return rows;
        }
        return rows.subList(rows.size() - count, rows.size());
    }

    private int countMatchingLines(Path path, String needle) {
        String normalized = needle.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String line : lines(path)) {
            if (line.toLowerCase(Locale.ROOT).contains(normalized)) {
                count++;
            }
        }
        return count;
    }

    private boolean isRunAlive(Path pidPath) {
        if (!Files.isRegularFile(pidPath)) {
            return false;
        }
        return isProcessRunning(longValue(readJson(pidPath), "pid"));
    }

    private ActiveRun findActiveRun(Path outputDir) {
        if (!Files.isDirectory(outputDir)) {
            return null;
        }
        try (var stream = Files.list(outputDir)) {
            for (Path pidPath : stream
                .filter(path -> path.getFileName().toString().endsWith(".pid.json"))
                .sorted()
                .toList()) {
                try {
                    JsonNode pid = objectMapper.readTree(pidPath.toFile());
                    long pidValue = longValue(pid, "pid");
                    if (isProcessRunning(pidValue)) {
                        String runId = text(pid, "run_id", pidPath.getFileName().toString().replaceFirst("\\.pid\\.json$", ""));
                        return new ActiveRun(runId, pidValue, pidPath);
                    }
                } catch (IOException ignored) {
                    // Ignore stale or partially written pid files when checking the global runner lock.
                }
            }
            return null;
        } catch (IOException e) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "백테스트 실행 상태를 확인할 수 없습니다: " + outputDir, e);
        }
    }

    private static boolean isProcessRunning(long pid) {
        return pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "JSON 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private static Path path(JsonNode node, String field, Path defaultPath) {
        String value = text(node, field, null);
        return value == null || value.isBlank() ? defaultPath : Path.of(value);
    }

    private static String firstText(JsonNode first, JsonNode second) {
        if (!first.isMissingNode() && !first.isNull() && !first.asText().isBlank()) {
            return first.asText();
        }
        if (!second.isMissingNode() && !second.isNull() && !second.asText().isBlank()) {
            return second.asText();
        }
        return null;
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? 0L : value.asLong();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText();
        return text.isBlank() ? null : Boolean.parseBoolean(text);
    }

    private static boolean boolValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return !node.isMissingNode() && !node.isNull() && Boolean.parseBoolean(node.asText());
    }

    private static boolean isNonEmptyPlan(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isArray() || node.isObject()) {
            return node.size() > 0;
        }
        return !node.asText().isBlank();
    }

    private static String modifiedAt(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(path).toInstant().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String eta(JsonNode pid, int rawRows) {
        Integer expected = integer(pid, "expected_rows");
        if (expected == null || expected <= rawRows || rawRows <= 0) {
            return null;
        }
        String started = text(pid, "started_at", null);
        if (started == null) {
            return null;
        }
        try {
            Instant start = OffsetDateTime.parse(started).toInstant();
            Duration elapsed = Duration.between(start, Instant.now());
            double rowsPerSecond = rawRows / Math.max(1.0, elapsed.toSeconds());
            long seconds = Math.round((expected - rawRows) / rowsPerSecond);
            return Duration.ofSeconds(seconds).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record RawStats(
        int rawRows,
        String lastDate,
        boolean prefixMatch,
        Map<String, Integer> decisions,
        Map<String, Integer> branches,
        int nonemptyRebalanceCount,
        int emptyRebalanceCount,
        int userDecisionRequiredCount,
        int issueStateSuppressionCount,
        Set<String> round1Agents
    ) {
    }

    private record UsageStats(
        int requests,
        long inputTokens,
        long outputTokens,
        long cacheCreationInputTokens,
        long cacheReadInputTokens
    ) {
    }

    private record ActiveRun(String runId, long pid, Path pidPath) {
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }
}
