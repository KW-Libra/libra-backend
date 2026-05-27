package com.libra.api.backtest.api.dto;

import java.util.List;
import java.util.Map;

public record BacktestRunStatusResponse(
    String runId,
    String status,
    Long pid,
    String startedAt,
    String model,
    String governancePreset,
    String executionPolicyMode,
    Boolean issueStateEnabled,
    Boolean executionResolveTickerConflicts,
    String decisionFrequency,
    Integer decisionInterval,
    String startDate,
    String endDate,
    Integer expectedRows,
    Integer rawRows,
    String lastDate,
    Boolean prefixMatch,
    Map<String, Integer> decisionDistribution,
    Map<String, Integer> governanceBranchDistribution,
    Integer nonemptyRebalanceCount,
    Integer emptyRebalanceCount,
    Integer userDecisionRequiredCount,
    Integer issueStateSuppressionCount,
    Integer round1AgentCount,
    List<String> round1Agents,
    Integer fallbackEventCount,
    Integer usageRequestCount,
    Long inputTokens,
    Long outputTokens,
    Long cacheCreationInputTokens,
    Long cacheReadInputTokens,
    String rawUpdatedAt,
    String eta,
    String rawOut,
    String usageLog,
    String traceOut,
    String stdoutLog,
    String stderrLog,
    List<String> stdoutTail,
    List<String> stderrTail
) {
}
