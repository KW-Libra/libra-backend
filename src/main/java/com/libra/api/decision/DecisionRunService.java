package com.libra.api.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.integration.agent.AgentGateway;
import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DecisionRunService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DecisionRunRepository decisionRunRepository;
    private final AgentSignalRepository agentSignalRepository;
    private final RebalancePlanItemRepository rebalancePlanItemRepository;
    private final DecisionEvaluationRepository decisionEvaluationRepository;
    private final AgentGateway agentGateway;
    private final ObjectMapper objectMapper;

    public DecisionRunService(
            DecisionRunRepository decisionRunRepository,
            AgentSignalRepository agentSignalRepository,
            RebalancePlanItemRepository rebalancePlanItemRepository,
            DecisionEvaluationRepository decisionEvaluationRepository,
            AgentGateway agentGateway,
            ObjectMapper objectMapper
    ) {
        this.decisionRunRepository = decisionRunRepository;
        this.agentSignalRepository = agentSignalRepository;
        this.rebalancePlanItemRepository = rebalancePlanItemRepository;
        this.decisionEvaluationRepository = decisionEvaluationRepository;
        this.agentGateway = agentGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DecisionRunRecord record(
            JudgeRunDispatchRequest request,
            PortfolioSnapshot portfolio,
            Map<String, Object> result
    ) {
        LocalDateTime now = LocalDateTime.now(SEOUL);
        Map<String, Object> decision = asMap(result.get("decision"));
        Map<String, Object> runtime = asMap(result.get("runtime"));
        Map<String, Object> knowledgeSources = asMap(result.get("knowledge_sources"));

        String id = UUID.randomUUID().toString();
        String threadId = asString(runtime.get("thread_id"), request.threadId());
        if (!StringUtils.hasText(threadId)) {
            threadId = id;
        }

        DecisionRunEntity run = new DecisionRunEntity(
                id,
                threadId,
                request.query(),
                asString(result.get("model"), "unknown"),
                asString(decision.get("trigger"), asString(request.trigger(), "pull")),
                asString(decision.get("decision"), "DEFER"),
                asString(decision.get("urgency"), "defer"),
                decimal(decision.get("confidence"), 0.0d, 5),
                decimal(decision.get("consensus_score"), 0.0d, 5),
                decimal(decision.get("divergence_score"), 0.0d, 5),
                asBoolean(decision.get("needs_trade_evaluation")),
                parseDateTime(decision.get("follow_up_at")),
                parseDateTime(decision.get("feedback_checkpoint")),
                writeJson(portfolio),
                writeJson(request),
                writeJson(result),
                writeJson(knowledgeSources),
                now,
                now
        );
        decisionRunRepository.save(run);
        recordAgentSignals(id, result, now);
        recordRebalancePlan(id, decision, now);
        return new DecisionRunRecord(id, threadId);
    }

    @Transactional(readOnly = true)
    public List<DecisionRunSummary> recent() {
        return decisionRunRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(DecisionRunSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DecisionRunDetail getDetail(String id) {
        DecisionRunEntity run = decisionRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Decision run not found: " + id));
        Map<String, Object> result = readResult(run.getResultPayload());
        Map<String, Object> decision = asMap(result.get("decision"));
        List<String> calledAgents = asList(decision.get("called_agents")).stream()
                .map(item -> asString(item, ""))
                .filter(StringUtils::hasText)
                .toList();
        return new DecisionRunDetail(
                DecisionRunSummary.from(run),
                calledAgents,
                rebalancePlan(decision),
                result
        );
    }

    @Transactional
    public DecisionEvaluationResult evaluate(String id, DecisionEvaluationRequest request) {
        DecisionRunEntity run = decisionRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Decision run not found: " + id));
        String horizon = asString(request.horizon(), "1w");
        Map<String, Object> storedResult = readResult(run.getResultPayload());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision_run_id", id);
        payload.put("horizon", horizon);
        payload.put("realized_return_pct", request.realizedReturnPct());
        payload.put("cost_pct", request.costPct() == null ? 0.0d : request.costPct());
        if (StringUtils.hasText(request.userFeedback())) {
            payload.put("user_feedback", request.userFeedback());
        }
        payload.put("decision_run_result", storedResult);

        Map<String, Object> evaluation = agentGateway.evaluate(payload);
        String evaluatedHorizon = asString(evaluation.get("horizon"), horizon);
        LocalDateTime now = LocalDateTime.now(SEOUL);

        decisionEvaluationRepository.findByDecisionRunIdAndHorizon(id, evaluatedHorizon)
                .ifPresent(existing -> {
                    decisionEvaluationRepository.delete(existing);
                    decisionEvaluationRepository.flush();
                });

        DecisionEvaluationEntity entity = new DecisionEvaluationEntity(
                id,
                evaluatedHorizon,
                now,
                asNullableBoolean(evaluation.get("direction_accuracy")),
                decimalOrNull(evaluation.get("timing_accuracy"), 5),
                decimalOrNull(evaluation.get("magnitude_error"), 6),
                decimalOrNull(evaluation.get("cost_efficiency"), 6),
                asNullableBoolean(evaluation.get("fast_track_accuracy")),
                asString(evaluation.get("verdict"), "BLOCKED"),
                asString(evaluation.get("note"), null),
                writeJson(evaluation),
                now
        );
        DecisionEvaluationEntity saved = decisionEvaluationRepository.save(entity);
        return DecisionEvaluationResult.from(saved, evaluation);
    }

    @Transactional(readOnly = true)
    public List<DecisionEvaluationResult> evaluations(String id) {
        if (!decisionRunRepository.existsById(id)) {
            throw new IllegalArgumentException("Decision run not found: " + id);
        }
        return decisionEvaluationRepository.findByDecisionRunIdOrderByEvaluatedAtDesc(id)
                .stream()
                .map(entity -> DecisionEvaluationResult.from(entity, readResult(entity.getMetricsPayload())))
                .toList();
    }

    private void recordAgentSignals(String runId, Map<String, Object> result, LocalDateTime now) {
        List<AgentSignalEntity> entities = new ArrayList<>();
        for (Object item : asList(result.get("agent_responses"))) {
            Map<String, Object> response = asMap(item);
            if (response.isEmpty()) {
                continue;
            }
            Map<String, Object> evidence = asMap(response.get("evidence"));
            String agentId = asString(response.get("agent_id"), "unknown");
            BigDecimal direction = decimal(response.get("direction"), 0.0d, 5);
            BigDecimal strength = decimal(response.get("strength"), 0.0d, 5);
            BigDecimal confidence = decimal(response.get("confidence"), 0.0d, 5);
            BigDecimal sourceTrust = sourceTrust(agentId);
            BigDecimal signalScore = signalScore(response, evidence, direction, strength, confidence, sourceTrust);

            entities.add(new AgentSignalEntity(
                    runId,
                    agentId,
                    asString(response.get("opinion_id"), UUID.randomUUID().toString()),
                    asInt(response.get("turn_number")),
                    asString(response.get("verdict"), "PARTIAL_ANSWER"),
                    direction,
                    strength,
                    asString(response.get("urgency"), "defer"),
                    confidence,
                    signalScore,
                    sourceTrust,
                    firstText(evidence, "event_type", "dominant_event_type"),
                    firstText(evidence, "horizon", "time_horizon"),
                    writeJson(response.get("focus_tickers")),
                    writeJson(evidence),
                    writeJson(response.get("tools_called")),
                    asString(response.get("reasoning_for_judge_agent"), ""),
                    asString(response.get("limits_acknowledged"), null),
                    now
            ));
        }
        agentSignalRepository.saveAll(entities);
    }

    private void recordRebalancePlan(String runId, Map<String, Object> decision, LocalDateTime now) {
        List<RebalancePlanItemEntity> entities = new ArrayList<>();
        for (Map.Entry<String, Double> entry : rebalancePlan(decision).entrySet()) {
            entities.add(new RebalancePlanItemEntity(
                    runId,
                    entry.getKey(),
                    BigDecimal.valueOf(entry.getValue()).setScale(6, RoundingMode.HALF_UP),
                    now
            ));
        }
        rebalancePlanItemRepository.saveAll(entities);
    }

    private Map<String, Double> rebalancePlan(Map<String, Object> decision) {
        Map<String, Double> plan = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : asMap(decision.get("candidate_rebalance_plan")).entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Number)) {
                continue;
            }
            plan.put(entry.getKey(), ((Number) value).doubleValue());
        }
        return plan;
    }

    private BigDecimal signalScore(
            Map<String, Object> response,
            Map<String, Object> evidence,
            BigDecimal direction,
            BigDecimal strength,
            BigDecimal confidence,
            BigDecimal sourceTrust
    ) {
        Object explicit = response.containsKey("signal_score") ? response.get("signal_score") : evidence.get("signal_score");
        if (explicit instanceof Number) {
            return decimal(explicit, 0.0d, 5);
        }
        return direction
                .multiply(strength)
                .multiply(confidence)
                .multiply(sourceTrust)
                .setScale(5, RoundingMode.HALF_UP);
    }

    private BigDecimal sourceTrust(String agentId) {
        return switch (agentId) {
            case "disclosure", "report" -> BigDecimal.valueOf(1.0d).setScale(5, RoundingMode.HALF_UP);
            case "news" -> BigDecimal.valueOf(0.7d).setScale(5, RoundingMode.HALF_UP);
            default -> BigDecimal.valueOf(0.5d).setScale(5, RoundingMode.HALF_UP);
        };
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            String value = asString(payload.get(key), null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> readResult(String payload) {
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored decision result is not valid JSON.", exception);
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize decision payload.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : defaultValue;
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Boolean asNullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private BigDecimal decimal(Object value, double defaultValue, int scale) {
        double numeric = defaultValue;
        if (value instanceof Number number) {
            numeric = number.doubleValue();
        } else if (value != null) {
            try {
                numeric = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                numeric = defaultValue;
            }
        }
        return BigDecimal.valueOf(numeric).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalOrNull(Object value, int scale) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(scale, RoundingMode.HALF_UP);
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(String.valueOf(value))).setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        String text = asString(value, null);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(SEOUL).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
