package com.libra.api.agent.api.dto;

import java.util.List;

public record AgentRunTranscriptResponse(
    AgentRunSummaryResponse run,
    List<AgentRunEventResponse> events
) {
}
