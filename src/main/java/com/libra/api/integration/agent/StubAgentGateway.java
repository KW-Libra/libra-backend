package com.libra.api.integration.agent;

import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StubAgentGateway {

    public Map<String, Object> run(JudgeRunDispatchRequest request, PortfolioSnapshot portfolio) {
        return runWithReason(request, portfolio, "Spring Boot backend placeholder while libra-agent bridge is unavailable.");
    }

    public Map<String, Object> runWithReason(JudgeRunDispatchRequest request, PortfolioSnapshot portfolio, String bridgeReason) {
        OffsetDateTime now = OffsetDateTime.now();
        String trigger = StringUtils.hasText(request.trigger()) ? request.trigger() : "pull";
        String depth = StringUtils.hasText(request.depth()) ? request.depth() : "medium";
        String threadId = StringUtils.hasText(request.threadId()) ? request.threadId() : UUID.randomUUID().toString();

        Map<String, Object> traceNode = new LinkedHashMap<>();
        traceNode.put("turn_number", 0);
        traceNode.put("phase", "decision");
        traceNode.put("actor", "disclosure_agent");
        traceNode.put("query", request.query());
        traceNode.put("summary", "Stub run completed without calling LangGraph agents.");
        traceNode.put("context", bridgeReason);
        traceNode.put("note", "Portfolio state was accepted and a follow-up checkpoint was scheduled.");
        traceNode.put("references", List.of());
        traceNode.put("tools_called", List.of());

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("decision", "DEFER");
        decision.put("summary", "libra-agent call did not complete, so the backend recorded the request and deferred the decision.");
        decision.put("confidence", 0.0);
        decision.put("urgency", "defer");
        decision.put("called_agents", List.of());
        decision.put("skipped_agents", List.of(
                "disclosure_agent",
                "news_agent",
                "report_agent",
                "earnings_agent",
                "cost_agent"
        ));
        decision.put("skip_rationale", Map.of(
                "disclosure_agent", bridgeReason,
                "news_agent", bridgeReason,
                "report_agent", bridgeReason,
                "earnings_agent", bridgeReason,
                "cost_agent", bridgeReason
        ));
        decision.put("candidate_rebalance_plan", Map.of());
        decision.put("decision_trace", List.of(traceNode));
        decision.put("reasoning", bridgeReason);
        decision.put("user_notification", null);
        decision.put("follow_up_at", now.plusHours(1).toString());
        decision.put("feedback_checkpoint", null);
        decision.put("consensus_score", 0.0);
        decision.put("divergence_score", 0.0);
        decision.put("needs_trade_evaluation", false);
        decision.put("trigger", trigger);
        decision.put("trigger_event", request.triggerEvent());
        decision.put("deadline_at", request.deadlineSeconds() == null ? null : now.plusSeconds(request.deadlineSeconds()).toString());
        decision.put("elapsed_seconds", 0.0);
        decision.put("options", List.of(
                "keep_current_portfolio_snapshot",
                "retry_after_agent_bridge_is_ready"
        ));
        decision.put("auto_safeguards", Map.of());
        decision.put("notification_log", List.of());

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("engine", "spring-boot-stub");
        runtime.put("thread_id", threadId);
        runtime.put("checkpoint_path", null);
        runtime.put("interrupted", false);
        runtime.put("resume_required", false);
        runtime.put("interrupts", new ArrayList<>());
        runtime.put("human_response", null);
        runtime.put("agent_gateway_error", bridgeReason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", "libra-backend-stub");
        result.put("query", request.query());
        result.put("portfolio", portfolio);
        result.put("agent_responses", List.of());
        result.put("decision", decision);
        result.put("knowledge_sources", request.knowledgeSources() == null ? Map.of() : request.knowledgeSources());
        result.put("runtime", runtime);
        result.put("state_record", null);

        return result;
    }

    public Map<String, Object> evaluate(Map<String, Object> payload) {
        return evaluateWithReason(payload, "Spring Boot backend placeholder while libra-agent evaluation bridge is unavailable.");
    }

    public Map<String, Object> evaluateWithReason(Map<String, Object> payload, String bridgeReason) {
        Object horizonValue = payload == null ? null : payload.get("horizon");
        String horizon = horizonValue == null ? null : String.valueOf(horizonValue);
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (payload != null) {
            metrics.putAll(payload);
        }
        metrics.put("bridge_reason", bridgeReason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agent_id", "evaluation");
        result.put("horizon", StringUtils.hasText(horizon) ? horizon : "1w");
        result.put("verdict", "BLOCKED");
        result.put("direction_accuracy", null);
        result.put("magnitude_error", null);
        result.put("cost_efficiency", null);
        result.put("note", bridgeReason);
        result.put("metrics", metrics);
        return result;
    }
}
