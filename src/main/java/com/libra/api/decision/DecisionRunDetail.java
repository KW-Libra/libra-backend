package com.libra.api.decision;

import java.util.List;
import java.util.Map;

public record DecisionRunDetail(
        DecisionRunSummary summary,
        List<String> calledAgents,
        List<AgentSignalResponse> agentSignals,
        Map<String, Double> candidateRebalancePlan,
        List<DecisionExecutionResult> executions,
        Map<String, Object> result
) {
}
