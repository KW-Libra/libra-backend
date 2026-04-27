package com.libra.api.decision;

import jakarta.validation.constraints.NotNull;

public record DecisionEvaluationRequest(
        String horizon,
        @NotNull
        Double realizedReturnPct,
        Double costPct,
        String userFeedback
) {
}
