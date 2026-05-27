package com.libra.api.backtest.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.backtests.runner")
public record BacktestRunnerProperties(
    boolean enabled,
    Path agentRepoRoot,
    Path outputDir,
    Path fixtureFile,
    Path envFile,
    String pythonCommand,
    String defaultModel,
    String defaultGovernancePreset,
    String defaultExecutionPolicyMode,
    int statusTailLines
) {

    public BacktestRunnerProperties {
        if (agentRepoRoot == null) {
            agentRepoRoot = Path.of("/opt/libra/backtest-agent/app");
        }
        if (outputDir == null) {
            outputDir = Path.of("/opt/libra/backtests/kr-objective-2020-2023-opendart-googlenews");
        }
        if (fixtureFile == null) {
            fixtureFile = Path.of("comparison-fixture.pykrx-volume.strict.json");
        }
        if (envFile == null) {
            envFile = agentRepoRoot.resolve(".env.live.local");
        }
        if (pythonCommand != null && pythonCommand.isBlank()) {
            pythonCommand = null;
        }
        if (defaultModel == null || defaultModel.isBlank()) {
            defaultModel = "claude-haiku-4-5-20251001";
        }
        if (defaultGovernancePreset == null || defaultGovernancePreset.isBlank()) {
            defaultGovernancePreset = "aggressive";
        }
        if (defaultExecutionPolicyMode == null || defaultExecutionPolicyMode.isBlank()) {
            defaultExecutionPolicyMode = "RISK_TRIM_AND_REDISTRIBUTE";
        }
        if (statusTailLines <= 0) {
            statusTailLines = 20;
        }
    }
}
