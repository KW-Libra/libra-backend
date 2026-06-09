package com.libra.api.agent.api.dto;

import com.libra.api.agent.domain.AgentRun;
import java.time.Instant;
import java.util.UUID;

public record AgentRunSummaryResponse(
    UUID id,
    String threadId,
    String status,
    String query,
    String trigger,
    String finalDecision,
    String finalBranch,
    String summary,
    int eventCount,
    Instant createdAt,
    Instant completedAt
) {
    public static AgentRunSummaryResponse from(AgentRun run) {
        return new AgentRunSummaryResponse(
            run.getId(),
            run.getThreadId(),
            run.getStatus().name(),
            run.getQuery(),
            run.getTrigger(),
            run.getFinalDecision(),
            run.getFinalBranch(),
            run.getSummary(),
            run.getEventCount(),
            run.getCreatedAt(),
            run.getCompletedAt()
        );
    }
}
