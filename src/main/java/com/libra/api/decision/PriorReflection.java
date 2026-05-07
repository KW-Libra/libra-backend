package com.libra.api.decision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * One past decision's reflection, prepared for injection into the next Judge run.
 *
 * Sourced from {@code DecisionEvaluationEntity.metrics_payload} after the agent
 * generated the reflection (see TauricResearch/TradingAgents-style learning loop).
 */
public record PriorReflection(
    @JsonProperty("decision_run_id") String decisionRunId,
    @JsonProperty("evaluated_at") LocalDateTime evaluatedAt,
    String horizon,
    String decision,
    String verdict,
    @JsonProperty("realized_return_pct") double realizedReturnPct,
    String reflection
) {
}
