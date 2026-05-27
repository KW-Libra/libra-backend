package com.libra.api.backtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.TimeUnit;
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

    public BacktestRunStatusResponse start(BacktestRunStartRequest request) {
        ensureRunnerConfigured();
        validateRequest(request);

        Path agentRoot = properties.agentRepoRoot().normalize();
        Path script = agentRoot.resolve("scripts").resolve("start-claude-committee-full-replay.ps1").normalize();
        Path outputDir = properties.outputDir().normalize();
        Path envFile = properties.envFile().normalize();
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
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "백테스트 실행 스크립트를 찾을 수 없습니다: " + script);
        }
        if (!Files.isRegularFile(envFile)) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "Anthropic API 키 env 파일을 찾을 수 없습니다: " + envFile);
        }
        if (!Files.isRegularFile(outputDir.resolve("comparison-fixture.json"))) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_NOT_CONFIGURED, "comparison-fixture.json을 찾을 수 없습니다: " + outputDir);
        }
        if (isRunAlive(outputDir.resolve(runId + ".pid.json")) && !force) {
            throw new ApiException(ErrorCode.BACKTEST_RUNNER_CONFLICT, "이미 실행 중인 runId입니다: " + runId);
        }

        List<String> command = new ArrayList<>();
        command.add(properties.powershellCommand());
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(script.toString());
        addArg(command, "-OutDir", outputDir.toString());
        addArg(command, "-EnvFile", envFile.toString());
        addArg(command, "-Model", model);
        addArg(command, "-GovernancePreset", governancePreset);
        addArg(command, "-PromptVariant", request.promptVariant());
        addArg(command, "-ExecutionPolicyMode", executionPolicyMode);
        addArg(command, "-ExecutionParticipationRate", request.executionParticipationRate());
        addArg(command, "-ExecutionMaxAbsDeltaPct", request.executionMaxAbsDeltaPct());
        addArg(command, "-ExecutionResolveTickerConflicts", boolText(request.executionResolveTickerConflicts(), true));
        addArg(command, "-IssueStateEnabled", boolText(request.issueStateEnabled(), true));
        addArg(command, "-IssueStateCooldownObservations", intText(request.issueStateCooldownObservations(), 20));
        addArg(command, "-StartDate", request.startDate() == null ? null : request.startDate().toString());
        addArg(command, "-EndDate", request.endDate() == null ? null : request.endDate().toString());
        addArg(command, "-DecisionFrequency", frequency);
        addArg(command, "-DecisionInterval", Integer.toString(interval));
        if (request.limit() != null) {
            addArg(command, "-Limit", request.limit().toString());
        }
        addArg(command, "-RunId", runId);
        if (force) {
            command.add("-Force");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(agentRoot.toFile());
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new ApiException(ErrorCode.BACKTEST_RUNNER_FAILED, "백테스트 시작 스크립트가 60초 안에 반환되지 않았습니다");
            }
            if (process.exitValue() != 0) {
                throw new ApiException(
                    ErrorCode.BACKTEST_RUNNER_FAILED,
                    "백테스트 시작 실패: " + compact(stderr, stdout)
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

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }
}
