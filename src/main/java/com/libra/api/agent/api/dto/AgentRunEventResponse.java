package com.libra.api.agent.api.dto;

import java.time.Instant;

public record AgentRunEventResponse(
    int eventIndex,
    String eventType,
    Object data,
    Instant createdAt
) {
}
