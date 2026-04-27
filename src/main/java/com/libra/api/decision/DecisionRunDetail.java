package com.libra.api.decision;

import java.util.List;
import java.util.Map;

public record DecisionRunDetail(
        DecisionRunSummary summary,
        List<String> calledAgents,
        Map<String, Double> candidateRebalancePlan,
        Map<String, Object> result
) {
}
