package com.libra.api.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.integration.agent.AgentGateway;
import com.libra.api.integration.kis.KisCashOrderCommand;
import com.libra.api.integration.kis.KisCashOrderResult;
import com.libra.api.integration.kis.KisCashOrderService;
import com.libra.api.integration.kis.KisCredentialService;
import com.libra.api.integration.kis.KisCredentialStatus;
import com.libra.api.integration.kis.KisMarketPriceService;
import com.libra.api.integration.kis.KisProperties;
import com.libra.api.integration.kis.KisQuote;
import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioHolding;
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
import java.util.Set;
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
    private final DecisionExecutionRepository decisionExecutionRepository;
    private final AgentGateway agentGateway;
    private final KisCredentialService kisCredentialService;
    private final KisCashOrderService kisCashOrderService;
    private final KisMarketPriceService kisMarketPriceService;
    private final ObjectMapper objectMapper;

    public DecisionRunService(
            DecisionRunRepository decisionRunRepository,
            AgentSignalRepository agentSignalRepository,
            RebalancePlanItemRepository rebalancePlanItemRepository,
            DecisionEvaluationRepository decisionEvaluationRepository,
            DecisionExecutionRepository decisionExecutionRepository,
            AgentGateway agentGateway,
            KisCredentialService kisCredentialService,
            KisCashOrderService kisCashOrderService,
            KisMarketPriceService kisMarketPriceService,
            ObjectMapper objectMapper
    ) {
        this.decisionRunRepository = decisionRunRepository;
        this.agentSignalRepository = agentSignalRepository;
        this.rebalancePlanItemRepository = rebalancePlanItemRepository;
        this.decisionEvaluationRepository = decisionEvaluationRepository;
        this.decisionExecutionRepository = decisionExecutionRepository;
        this.agentGateway = agentGateway;
        this.kisCredentialService = kisCredentialService;
        this.kisCashOrderService = kisCashOrderService;
        this.kisMarketPriceService = kisMarketPriceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DecisionRunRecord record(
            String userId,
            JudgeRunDispatchRequest request,
            PortfolioSnapshot portfolio,
            Map<String, Object> result
    ) {
        requireUser(userId);
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
                userId,
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
    public List<DecisionRunSummary> recent(String userId) {
        requireUser(userId);
        return decisionRunRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DecisionRunSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DecisionRunDetail getDetail(String userId, String id) {
        DecisionRunEntity run = loadOwned(userId, id);
        Map<String, Object> result = readResult(run.getResultPayload());
        Map<String, Object> decision = asMap(result.get("decision"));
        List<String> calledAgents = asList(decision.get("called_agents")).stream()
                .map(item -> asString(item, ""))
                .filter(StringUtils::hasText)
                .toList();
        return new DecisionRunDetail(
                DecisionRunSummary.from(run),
                calledAgents,
                agentSignals(id),
                rebalancePlan(decision),
                executionResults(id),
                result
        );
    }

    @Transactional(readOnly = true)
    public List<DecisionExecutionResult> executions(String userId, String id) {
        loadOwned(userId, id);
        return executionResults(id);
    }

    @Transactional(readOnly = true)
    public List<DecisionExecutionProposalItem> proposeKisDemoOrders(String userId, String id) {
        DecisionRunEntity run = loadOwned(userId, id);
        requireDemoCredential(userId);

        Map<String, Object> result = readResult(run.getResultPayload());
        Map<String, Object> decision = asMap(result.get("decision"));
        Map<String, Double> plan = rebalancePlan(decision);
        if (plan.isEmpty()) {
            throw new IllegalArgumentException("Decision run has no candidate rebalance plan.");
        }

        PortfolioSnapshot portfolio = objectMapper.convertValue(asMap(result.get("portfolio")), PortfolioSnapshot.class);
        double baseValue = portfolioBaseValue(portfolio);
        if (baseValue <= 0) {
            throw new IllegalArgumentException("portfolio total value is required to propose KIS orders.");
        }

        KisProperties.Credential credential = kisCredentialService.runtimeCredential(userId, "demo")
                .orElseThrow(() -> new IllegalArgumentException("KIS demo credential is not stored."));
        Map<String, KisQuote> quotes = new LinkedHashMap<>();
        for (KisQuote quote : kisMarketPriceService.fetchDomesticQuotes("demo", new ArrayList<>(plan.keySet()), credential)) {
            quotes.put(quote.ticker(), quote);
        }

        List<DecisionExecutionProposalItem> proposals = new ArrayList<>();
        for (Map.Entry<String, Double> entry : plan.entrySet()) {
            String ticker = entry.getKey();
            double weightDelta = entry.getValue();
            KisQuote quote = quotes.get(ticker);
            double price = quote == null ? 0.0d : quote.priceKrw();
            String side = weightDelta >= 0 ? "BUY" : "SELL";
            long quantity = proposedQuantity(
                    ticker,
                    side,
                    Math.abs(weightDelta) * baseValue,
                    price,
                    portfolio
            );
            BigDecimal priceKrw = money(price);
            BigDecimal amountKrw = priceKrw.multiply(BigDecimal.valueOf(quantity));
            proposals.add(new DecisionExecutionProposalItem(
                    ticker,
                    side,
                    quantity,
                    priceKrw,
                    amountKrw,
                    "01",
                    weightDelta,
                    proposalReason(side, quantity, price, baseValue, weightDelta)
            ));
        }
        return proposals;
    }

    @Transactional
    public List<DecisionExecutionResult> executeKisDemoOrders(
            String userId,
            String id,
            DecisionExecutionRequest request
    ) {
        DecisionRunEntity run = loadOwned(userId, id);
        validateExecutableDecision(run);
        requireDemoCredential(userId);
        if (request.isDryRun()) {
            return request.orders().stream()
                    .map(item -> previewExecution(id, item))
                    .toList();
        }

        KisProperties.Credential credential = kisCredentialService.runtimeCredential(userId, "demo")
                .orElseThrow(() -> new IllegalArgumentException("KIS demo credential is not stored."));
        LocalDateTime now = LocalDateTime.now(SEOUL);
        List<DecisionExecutionResult> results = new ArrayList<>();
        for (DecisionExecutionOrderItem item : request.orders()) {
            KisCashOrderResult orderResult = kisCashOrderService.placeDomesticCashOrder(toCommand(item), credential);
            Map<String, Object> rawPayload = executionPayload(orderResult);
            DecisionExecutionEntity saved = decisionExecutionRepository.save(new DecisionExecutionEntity(
                    id,
                    orderResult.ticker(),
                    orderResult.side(),
                    orderResult.quantity(),
                    orderResult.priceKrw(),
                    orderResult.amountKrw(),
                    now,
                    orderResult.status(),
                    writeJson(rawPayload),
                    now
            ));
            results.add(DecisionExecutionResult.from(saved, rawPayload));
        }
        return results;
    }

    @Transactional
    public DecisionEvaluationResult evaluate(String userId, String id, DecisionEvaluationRequest request) {
        DecisionRunEntity run = loadOwned(userId, id);
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
    public List<DecisionEvaluationResult> evaluations(String userId, String id) {
        loadOwned(userId, id);
        return decisionEvaluationRepository.findByDecisionRunIdOrderByEvaluatedAtDesc(id)
                .stream()
                .map(entity -> DecisionEvaluationResult.from(entity, readResult(entity.getMetricsPayload())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriorReflection> recentReflectionsForUser(String userId, int limit) {
        requireUser(userId);
        if (limit <= 0) {
            return List.of();
        }
        List<DecisionEvaluationEntity> entries = decisionEvaluationRepository.findRecentByUserId(
                userId,
                org.springframework.data.domain.PageRequest.of(0, limit)
        );
        List<PriorReflection> result = new ArrayList<>(entries.size());
        for (DecisionEvaluationEntity entity : entries) {
            PriorReflection reflection = toPriorReflection(entity);
            if (reflection != null) {
                result.add(reflection);
            }
        }
        return result;
    }

    private PriorReflection toPriorReflection(DecisionEvaluationEntity entity) {
        Map<String, Object> payload;
        try {
            payload = readResult(entity.getMetricsPayload());
        } catch (RuntimeException ignored) {
            return null;
        }
        String reflectionText = asString(payload.get("reflection"), "").trim();
        if (reflectionText.isEmpty()) {
            return null;
        }
        Map<String, Object> metrics = asMap(payload.get("metrics"));
        String decision = asString(metrics.get("decision"), entity.getVerdict());
        double realizedReturnPct = asDouble(metrics.get("realized_return_pct"));
        return new PriorReflection(
                entity.getDecisionRunId(),
                entity.getEvaluatedAt(),
                entity.getHorizon(),
                decision,
                entity.getVerdict(),
                realizedReturnPct,
                reflectionText
        );
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private DecisionRunEntity loadOwned(String userId, String id) {
        requireUser(userId);
        DecisionRunEntity run = decisionRunRepository.findById(id)
                .orElseThrow(() -> new DecisionRunNotFoundException(id));
        if (!userId.equals(run.getUserId())) {
            throw new DecisionRunNotFoundException(id);
        }
        return run;
    }

    private static void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required for decision-run access");
        }
    }

    public static class DecisionRunNotFoundException extends RuntimeException {
        public DecisionRunNotFoundException(String id) {
            super("Decision run not found: " + id);
        }
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
            String agentKind = firstText(response, "agent_kind");
            if (!StringUtils.hasText(agentKind)) {
                agentKind = firstText(evidence, "agent_kind");
            }
            if (!StringUtils.hasText(agentKind)) {
                agentKind = agentKind(agentId);
            }
            String vote = firstText(response, "vote");
            if (!StringUtils.hasText(vote)) {
                vote = firstText(evidence, "vote");
            }
            String llmUsed = firstText(response, "llm_used", "model_used");
            if (!StringUtils.hasText(llmUsed)) {
                llmUsed = firstText(evidence, "llm_used", "model_used");
            }
            String domainSignalsJson = domainSignalsJson(response, evidence);
            BigDecimal direction = decimal(response.get("direction"), 0.0d, 5);
            BigDecimal strength = decimal(response.get("strength"), 0.0d, 5);
            BigDecimal confidence = decimal(response.get("confidence"), 0.0d, 5);
            BigDecimal sourceTrust = sourceTrust(agentId);
            BigDecimal signalScore = signalScore(response, evidence, direction, strength, confidence, sourceTrust);

            entities.add(new AgentSignalEntity(
                    runId,
                    agentId,
                    agentKind,
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
                    vote,
                    domainSignalsJson,
                    llmUsed,
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

    private List<AgentSignalResponse> agentSignals(String decisionRunId) {
        return agentSignalRepository.findByDecisionRunIdOrderByTurnNumberAscIdAsc(decisionRunId)
                .stream()
                .map(this::agentSignalResponse)
                .toList();
    }

    private AgentSignalResponse agentSignalResponse(AgentSignalEntity entity) {
        Object domainSignals = readOptionalJson(entity.getDomainSignalsJson());
        String domainSignalsJson = domainSignals == null ? null : writeJson(domainSignals);
        return AgentSignalResponse.from(entity, domainSignalsJson, domainSignals);
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

    private List<DecisionExecutionResult> executionResults(String decisionRunId) {
        return decisionExecutionRepository.findByDecisionRunIdOrderByCreatedAtDesc(decisionRunId)
                .stream()
                .map(entity -> DecisionExecutionResult.from(entity, readResult(entity.getRawPayload())))
                .toList();
    }

    private void requireDemoCredential(String userId) {
        KisCredentialStatus status = kisCredentialService.getStatus(userId);
        if (!status.configured()) {
            throw new IllegalArgumentException("KIS demo credential is not stored.");
        }
        if (!"demo".equals(status.environment())) {
            throw new IllegalArgumentException("KIS order execution is currently limited to demo credentials.");
        }
    }

    private void validateExecutableDecision(DecisionRunEntity run) {
        Map<String, Object> result = readResult(run.getResultPayload());
        Map<String, Object> decision = asMap(result.get("decision"));
        if (!"REBALANCE".equals(asString(decision.get("decision"), ""))) {
            throw new IllegalArgumentException("Only REBALANCE decision runs can be executed.");
        }
        if (rebalancePlan(decision).isEmpty()) {
            throw new IllegalArgumentException("REBALANCE decision run has no candidate rebalance plan.");
        }
        List<String> calledAgents = asList(decision.get("called_agents")).stream()
                .map(item -> asString(item, ""))
                .filter(StringUtils::hasText)
                .toList();
        if (!calledAgents.contains("profit") || !calledAgents.contains("cost")) {
            throw new IllegalArgumentException("KIS execution requires profit and cost agent review.");
        }
    }

    private DecisionExecutionResult previewExecution(String decisionRunId, DecisionExecutionOrderItem item) {
        BigDecimal price = item.priceKrw() == null ? BigDecimal.ZERO : item.priceKrw();
        BigDecimal quantity = BigDecimal.valueOf(item.quantity());
        BigDecimal amount = price.multiply(quantity);
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("message", "dry run only; no KIS order was submitted");
        rawPayload.put("order_type", orderType(item));
        return new DecisionExecutionResult(
                null,
                decisionRunId,
                item.ticker(),
                item.side().toUpperCase(),
                quantity,
                price,
                amount,
                null,
                "DRY_RUN",
                "dry run only; no KIS order was submitted",
                null,
                rawPayload,
                LocalDateTime.now(SEOUL)
        );
    }

    private KisCashOrderCommand toCommand(DecisionExecutionOrderItem item) {
        return new KisCashOrderCommand(
                item.ticker(),
                item.side(),
                item.quantity(),
                item.priceKrw(),
                orderType(item)
        );
    }

    private String orderType(DecisionExecutionOrderItem item) {
        return StringUtils.hasText(item.orderType()) ? item.orderType() : "01";
    }

    private double portfolioBaseValue(PortfolioSnapshot portfolio) {
        if (portfolio == null) {
            return 0.0d;
        }
        if (portfolio.totalValueKrw() != null && portfolio.totalValueKrw() > 0) {
            return portfolio.totalValueKrw();
        }
        if (portfolio.holdings() == null) {
            return 0.0d;
        }
        return portfolio.holdings().stream()
                .map(PortfolioHolding::marketValueKrw)
                .filter(value -> value != null && value > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private long proposedQuantity(
            String ticker,
            String side,
            double targetAmountKrw,
            double priceKrw,
            PortfolioSnapshot portfolio
    ) {
        if (targetAmountKrw <= 0 || priceKrw <= 0) {
            return 0L;
        }
        long quantity = (long) Math.floor(targetAmountKrw / priceKrw);
        if ("SELL".equals(side)) {
            return Math.min(quantity, availableShares(ticker, portfolio));
        }
        return Math.max(quantity, 0L);
    }

    private long availableShares(String ticker, PortfolioSnapshot portfolio) {
        if (portfolio == null || portfolio.holdings() == null) {
            return 0L;
        }
        return portfolio.holdings().stream()
                .filter(holding -> ticker.equals(holding.ticker()))
                .map(PortfolioHolding::shares)
                .filter(value -> value != null && value > 0)
                .map(value -> (long) Math.floor(value))
                .findFirst()
                .orElse(0L);
    }

    private BigDecimal money(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }

    private String proposalReason(
            String side,
            long quantity,
            double priceKrw,
            double baseValue,
            double weightDelta
    ) {
        if (priceKrw <= 0) {
            return "KIS current price was unavailable, so quantity is 0.";
        }
        if (quantity <= 0) {
            return "Weight delta was too small or available shares were insufficient.";
        }
        return "Computed from "
                + side
                + " weight delta "
                + BigDecimal.valueOf(weightDelta).setScale(6, RoundingMode.HALF_UP)
                + ", base portfolio value "
                + money(baseValue)
                + ", KIS price "
                + money(priceKrw)
                + ".";
    }

    private Map<String, Object> executionPayload(KisCashOrderResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", result.message());
        payload.put("order_no", result.orderNo());
        payload.put("order_time", result.orderTime());
        payload.put("kis", result.rawPayload());
        return payload;
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

    private String agentKind(String agentId) {
        if (Set.of("disclosure", "news", "report").contains(agentId)) {
            return "information";
        }
        if (Set.of("profit", "cost").contains(agentId)) {
            return "trade";
        }
        if ("evaluation".equals(agentId)) {
            return "evaluation";
        }
        return "domain";
    }

    private String domainSignalsJson(Map<String, Object> response, Map<String, Object> evidence) {
        Object signals = firstPresent(
                evidence,
                "domain_signals_json",
                "domain_signals",
                "signals"
        );
        if (signals == null) {
            signals = firstPresent(
                    response,
                    "domain_signals_json",
                    "domain_signals",
                    "signals"
            );
        }
        if (signals == null) {
            return null;
        }
        return jsonValue(signals);
    }

    private Object firstPresent(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key) && payload.get(key) != null) {
                return payload.get(key);
            }
        }
        return null;
    }

    private String jsonValue(Object value) {
        return writeJson(normalizeJsonValue(value));
    }

    private boolean looksLikeJson(String value) {
        String text = value.trim();
        return text.startsWith("{") || text.startsWith("[") || text.startsWith("\"");
    }

    private Object normalizeJsonValue(Object value) {
        Object current = value;
        for (int i = 0; i < 3; i++) {
            if (!(current instanceof String text) || !looksLikeJson(text)) {
                return current;
            }
            try {
                current = objectMapper.readValue(text, Object.class);
            } catch (JsonProcessingException ignored) {
                return current;
            }
        }
        return current;
    }

    private Object readOptionalJson(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        return normalizeJsonValue(payload);
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
