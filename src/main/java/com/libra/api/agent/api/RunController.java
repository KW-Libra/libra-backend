package com.libra.api.agent.api;

import com.libra.api.agent.api.dto.ResumeRequest;
import com.libra.api.agent.api.dto.RunStartRequest;
import com.libra.api.agent.service.AgentSseClient;
import com.libra.api.auth.domain.User;
import com.libra.api.common.correlation.CorrelationIdFilter;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/runs")
@Tag(name = "Runs")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class RunController {

    private final AgentSseClient agent;

    public RunController(AgentSseClient agent) {
        this.agent = agent;
    }

    @Operation(summary = "Start an agent run and stream events")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startRun(@RequestBody @Valid RunStartRequest req,
                               @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return agent.startRun(req, requireUser(user), traceId());
    }

    @Operation(summary = "Resume a paused agent run and stream events")
    @PostMapping(path = "/{threadId}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeRun(@PathVariable String threadId,
                                @RequestBody @Valid ResumeRequest req,
                                @Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return agent.resumeRun(threadId, req, requireUser(user), traceId());
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return user;
    }

    private static String traceId() {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return traceId == null || traceId.isBlank() ? "missing-trace-id" : traceId;
    }
}
