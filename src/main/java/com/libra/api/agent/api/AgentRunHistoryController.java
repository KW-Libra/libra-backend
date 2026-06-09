package com.libra.api.agent.api;

import com.libra.api.agent.api.dto.AgentRunEventResponse;
import com.libra.api.agent.api.dto.AgentRunSummaryResponse;
import com.libra.api.agent.api.dto.AgentRunTranscriptResponse;
import com.libra.api.agent.domain.AgentRun;
import com.libra.api.agent.service.AgentRunService;
import com.libra.api.auth.domain.User;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/agent-runs")
@Tag(name = "Agent Run History")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AgentRunHistoryController {

    private final AgentRunService service;
    private final ObjectMapper objectMapper;

    public AgentRunHistoryController(AgentRunService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "List the current user's past deliberation runs")
    @GetMapping
    public Page<AgentRunSummaryResponse> list(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return service.listRuns(requireUser(user).getId(), pageable).map(AgentRunSummaryResponse::from);
    }

    @Operation(summary = "Get the full event transcript of a past run")
    @GetMapping("/{runId}/transcript")
    public AgentRunTranscriptResponse transcript(
        @Parameter(hidden = true) @AuthenticationPrincipal User user,
        @PathVariable UUID runId
    ) {
        UUID userId = requireUser(user).getId();
        AgentRun run = service.getRun(userId, runId);
        List<AgentRunEventResponse> events = service.transcript(userId, runId).stream()
            .map(event -> new AgentRunEventResponse(
                event.getEventIndex(),
                event.getEventType(),
                parseData(event.getEventData()),
                event.getCreatedAt()))
            .toList();
        return new AgentRunTranscriptResponse(AgentRunSummaryResponse.from(run), events);
    }

    private Object parseData(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JacksonException e) {
            return json;
        }
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return user;
    }
}
