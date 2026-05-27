package com.libra.api.backtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.libra.api.backtest.api.dto.BacktestRunStatusResponse;
import com.libra.api.backtest.api.dto.BacktestRunStartRequest;
import com.libra.api.backtest.config.BacktestRunnerProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BacktestRunServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void readsRunStatusFromReplayArtifacts() throws Exception {
        String runId = "test-run";
        Path raw = tempDir.resolve("libra-replay-results." + runId + ".jsonl");
        Path usage = tempDir.resolve("anthropic-" + runId + ".usage.jsonl");
        Path trace = tempDir.resolve(runId + ".trace.jsonl");
        Path stdout = tempDir.resolve(runId + ".stdout.log");
        Path stderr = tempDir.resolve(runId + ".stderr.log");

        Files.writeString(
            tempDir.resolve("comparison-fixture.json"),
            """
            {"prices":[{"date":"2020-01-02"},{"date":"2020-01-03"}]}
            """
        );
        Files.writeString(
            tempDir.resolve(runId + ".pid.json"),
            """
            {
              "run_id": "test-run",
              "pid": 99999999,
              "started_at": "2026-05-27T12:00:00+09:00",
              "model": "claude-haiku-4-5-20251001",
              "governance_preset": "aggressive",
              "execution_policy_mode": "RISK_TRIM_AND_REDISTRIBUTE",
              "issue_state_enabled": "true",
              "execution_resolve_ticker_conflicts": "true",
              "decision_frequency": "every-n-trading-days",
              "decision_interval": 5,
              "expected_rows": 2,
              "fixture": "%s",
              "raw_out": "%s",
              "usage_log": "%s",
              "trace_out": "%s",
              "stdout_log": "%s",
              "stderr_log": "%s"
            }
            """.formatted(
                tempDir.resolve("comparison-fixture.json").toString().replace("\\", "\\\\"),
                raw.toString().replace("\\", "\\\\"),
                usage.toString().replace("\\", "\\\\"),
                trace.toString().replace("\\", "\\\\"),
                stdout.toString().replace("\\", "\\\\"),
                stderr.toString().replace("\\", "\\\\")
            )
        );
        Files.writeString(
            raw,
            """
            {"date":"2020-01-02","result":{"agent_responses":[{"agent_id":"risk"}],"decision":{"decision":"DEFER","candidate_rebalance_plan":{},"auto_safeguards":{"governance_v1_branch":"NO_EXECUTABLE_TRADE"}},"governance_v1":{"final_decision":{"decision":"DEFER","branch":"NO_EXECUTABLE_TRADE","trades":[]}}}}
            {"date":"2020-01-03","result":{"agent_responses":[{"agent_id":"risk"},{"agent_id":"news"}],"decision":{"decision":"REBALANCE","candidate_rebalance_plan":{"005930":-0.02},"auto_safeguards":{"governance_v1_branch":"CONFLICT_RESOLUTION"}},"governance_v1":{"final_decision":{"decision":"REBALANCE","branch":"CONFLICT_RESOLUTION","trades":[{"ticker":"005930"}]}}}}
            """
        );
        Files.writeString(
            usage,
            """
            {"usage":{"input_tokens":10,"output_tokens":5,"cache_creation_input_tokens":2,"cache_read_input_tokens":3}}
            {"usage":{"input_tokens":11,"output_tokens":7,"cache_creation_input_tokens":0,"cache_read_input_tokens":4}}
            """
        );
        Files.writeString(trace, "{\"event\":\"fallback_checked\"}\n");
        Files.writeString(stdout, "row 1\nrow 2\n");
        Files.writeString(stderr, "");

        BacktestRunService service = new BacktestRunService(new BacktestRunnerProperties(
            true,
            tempDir,
            tempDir,
            tempDir.resolve(".env.live.local"),
            null,
            "claude-haiku-4-5-20251001",
            "aggressive",
            "RISK_TRIM_AND_REDISTRIBUTE",
            10
        ));

        BacktestRunStatusResponse status = service.status(runId);

        assertThat(status.status()).isEqualTo("EXITED");
        assertThat(status.rawRows()).isEqualTo(2);
        assertThat(status.lastDate()).isEqualTo("2020-01-03");
        assertThat(status.prefixMatch()).isTrue();
        assertThat(status.decisionDistribution()).containsEntry("DEFER", 1).containsEntry("REBALANCE", 1);
        assertThat(status.governanceBranchDistribution()).containsEntry("CONFLICT_RESOLUTION", 1);
        assertThat(status.nonemptyRebalanceCount()).isEqualTo(1);
        assertThat(status.round1Agents()).containsExactly("risk", "news");
        assertThat(status.usageRequestCount()).isEqualTo(2);
        assertThat(status.inputTokens()).isEqualTo(21);
        assertThat(status.outputTokens()).isEqualTo(12);
        assertThat(status.fallbackEventCount()).isEqualTo(1);
    }

    @Test
    void rejectsStartingAnyRunWhenAnotherBacktestProcessIsAlive() throws Exception {
        Files.createDirectories(tempDir.resolve("scripts"));
        Files.writeString(tempDir.resolve("scripts").resolve("replay_full_committee_backtest.py"), "print('unused')\n");
        Files.writeString(tempDir.resolve(".env.live.local"), "ANTHROPIC_API_KEY=test-key\n");
        Files.writeString(tempDir.resolve("comparison-fixture.json"), "{\"prices\":[{\"date\":\"2020-01-02\"}]}\n");
        Files.createDirectories(tempDir.resolve("ingest-bundles-article"));
        Files.writeString(tempDir.resolve("ingest-bundles-article").resolve("index.json"), "{}\n");
        Files.writeString(
            tempDir.resolve("already-running.pid.json"),
            """
            {
              "run_id": "already-running",
              "pid": %d,
              "expected_rows": 10
            }
            """.formatted(ProcessHandle.current().pid())
        );

        BacktestRunService service = new BacktestRunService(new BacktestRunnerProperties(
            true,
            tempDir,
            tempDir,
            tempDir.resolve(".env.live.local"),
            "python",
            "claude-haiku-4-5-20251001",
            "aggressive",
            "RISK_TRIM_AND_REDISTRIBUTE",
            10
        ));

        assertThatThrownBy(() -> service.start(new BacktestRunStartRequest(
            "new-run",
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            true,
            20,
            LocalDate.parse("2020-01-02"),
            LocalDate.parse("2020-01-02"),
            "daily",
            1,
            1,
            true
        )))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo(ErrorCode.BACKTEST_RUNNER_CONFLICT))
            .hasMessageContaining("already-running");
    }
}
