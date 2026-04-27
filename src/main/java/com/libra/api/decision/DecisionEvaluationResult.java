package com.libra.api.decision;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record DecisionEvaluationResult(
        Long id,
        String decisionRunId,
        String horizon,
        LocalDateTime evaluatedAt,
        Boolean directionAccuracy,
        BigDecimal timingAccuracy,
        BigDecimal magnitudeError,
        BigDecimal costEfficiency,
        Boolean fastTrackAccuracy,
        String verdict,
        String notes,
        Map<String, Object> metrics,
        LocalDateTime createdAt
) {
    static DecisionEvaluationResult from(DecisionEvaluationEntity entity, Map<String, Object> metrics) {
        return new DecisionEvaluationResult(
                entity.getId(),
                entity.getDecisionRunId(),
                entity.getHorizon(),
                entity.getEvaluatedAt(),
                entity.getDirectionAccuracy(),
                entity.getTimingAccuracy(),
                entity.getMagnitudeError(),
                entity.getCostEfficiency(),
                entity.getFastTrackAccuracy(),
                entity.getVerdict(),
                entity.getNotes(),
                metrics,
                entity.getCreatedAt()
        );
    }
}
