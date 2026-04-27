package com.libra.api.decision;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DecisionRunSummary(
        String id,
        String threadId,
        String query,
        String model,
        String triggerType,
        String decision,
        String urgency,
        BigDecimal confidence,
        BigDecimal consensusScore,
        BigDecimal divergenceScore,
        boolean needsTradeEvaluation,
        LocalDateTime followUpAt,
        LocalDateTime feedbackCheckpointAt,
        LocalDateTime createdAt
) {
    static DecisionRunSummary from(DecisionRunEntity entity) {
        return new DecisionRunSummary(
                entity.getId(),
                entity.getThreadId(),
                entity.getQuery(),
                entity.getModel(),
                entity.getTriggerType(),
                entity.getDecision(),
                entity.getUrgency(),
                entity.getConfidence(),
                entity.getConsensusScore(),
                entity.getDivergenceScore(),
                entity.isNeedsTradeEvaluation(),
                entity.getFollowUpAt(),
                entity.getFeedbackCheckpointAt(),
                entity.getCreatedAt()
        );
    }
}
