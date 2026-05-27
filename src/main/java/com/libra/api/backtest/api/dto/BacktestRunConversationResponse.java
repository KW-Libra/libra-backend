package com.libra.api.backtest.api.dto;

import java.util.List;
import java.util.Map;

public record BacktestRunConversationResponse(
    String runId,
    String selectedDate,
    List<BacktestRunDaySummary> days,
    BacktestRunDayConversation conversation
) {
    public record BacktestRunDaySummary(
        String date,
        String decision,
        String branch,
        Integer agentCount,
        Integer tradeCount,
        Boolean userActionRequired
    ) {
    }

    public record BacktestRunDayConversation(
        String date,
        String query,
        String model,
        BacktestRunFinalDecision finalDecision,
        List<BacktestRunAgentMessage> agents,
        List<BacktestRunAgentMessage> round2Agents,
        Map<String, Object> executionPlan
    ) {
    }

    public record BacktestRunFinalDecision(
        String decision,
        String branch,
        String reasoning,
        String userQuestion,
        List<Map<String, Object>> trades
    ) {
    }

    public record BacktestRunAgentMessage(
        String agentId,
        String opinion,
        String verdict,
        Double confidence,
        Double direction,
        String riskLevel,
        List<String> focusTickers,
        String queryUnderstood,
        String reasoning,
        String limitsAcknowledged,
        List<BacktestRunToolCall> tools
    ) {
    }

    public record BacktestRunToolCall(
        String toolName,
        String purpose,
        String summary
    ) {
    }
}
