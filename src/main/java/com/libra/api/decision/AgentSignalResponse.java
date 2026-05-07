package com.libra.api.decision;

import java.math.BigDecimal;

public record AgentSignalResponse(
        String agentId,
        String agentKind,
        String opinionId,
        int turnNumber,
        String verdict,
        BigDecimal direction,
        BigDecimal strength,
        String urgency,
        BigDecimal confidence,
        BigDecimal signalScore,
        BigDecimal sourceTrust,
        String eventType,
        String horizon,
        String vote,
        String domainSignalsJson,
        Object domainSignals,
        String llmUsed,
        String reasoning
) {
    public static AgentSignalResponse from(AgentSignalEntity entity, Object domainSignals) {
        return from(entity, entity.getDomainSignalsJson(), domainSignals);
    }

    public static AgentSignalResponse from(AgentSignalEntity entity, String domainSignalsJson, Object domainSignals) {
        return new AgentSignalResponse(
                entity.getAgentId(),
                entity.getAgentKind(),
                entity.getOpinionId(),
                entity.getTurnNumber(),
                entity.getVerdict(),
                entity.getDirection(),
                entity.getStrength(),
                entity.getUrgency(),
                entity.getConfidence(),
                entity.getSignalScore(),
                entity.getSourceTrust(),
                entity.getEventType(),
                entity.getHorizon(),
                entity.getVote(),
                domainSignalsJson,
                domainSignals,
                entity.getLlmUsed(),
                entity.getReasoning()
        );
    }
}
