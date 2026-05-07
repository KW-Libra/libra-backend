package com.libra.api.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.libra.api.auth.UserEntity;
import com.libra.api.auth.UserRepository;
import com.libra.api.integration.agent.AgentGateway;
import com.libra.api.integration.kis.KisCashOrderResult;
import com.libra.api.integration.kis.KisCashOrderService;
import com.libra.api.integration.kis.KisCredentialRequest;
import com.libra.api.integration.kis.KisCredentialService;
import com.libra.api.integration.kis.KisMarketPriceService;
import com.libra.api.integration.kis.KisQuote;
import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "libra.agent.base-url=http://127.0.0.1:1",
        "libra.agent.connect-timeout-ms=100",
        "libra.agent.read-timeout-ms=100"
})
@Transactional
class DecisionRunServiceTest {

    @Autowired
    private DecisionRunService decisionRunService;

    @Autowired
    private DecisionRunRepository decisionRunRepository;

    @Autowired
    private AgentSignalRepository agentSignalRepository;

    @Autowired
    private RebalancePlanItemRepository rebalancePlanItemRepository;

    @Autowired
    private DecisionEvaluationRepository decisionEvaluationRepository;

    @Autowired
    private DecisionExecutionRepository decisionExecutionRepository;

    @Autowired
    private KisCredentialService kisCredentialService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private AgentGateway agentGateway;

    @MockBean
    private KisCashOrderService kisCashOrderService;

    @MockBean
    private KisMarketPriceService kisMarketPriceService;

    private String userId;

    @BeforeEach
    void setUp() {
        UserEntity user = userRepository.save(UserEntity.newLocalUser(
                "decision-test@example.com",
                "$2a$10$dummy.hash.for.test.purposes.only0000000000000000000",
                "Decision Test"
        ));
        userId = user.id();
    }

    @Test
    void recordsDecisionRunSignalsAndCandidatePlan() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.6d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.4d,
                List.of("초보자")
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                portfolio,
                Map.of("events", "s3://libra/events/latest.json"),
                "medium",
                "pull",
                null,
                null,
                "thread-123",
                false,
                null,
                null,
                null
        );
        Map<String, Object> result = Map.of(
                "model", "test-model",
                "query", request.query(),
                "portfolio", portfolio,
                "agent_responses", List.of(Map.ofEntries(
                        Map.entry("agent_id", "news"),
                        Map.entry("opinion_id", "opinion-1"),
                        Map.entry("turn_number", 1),
                        Map.entry("query_understood", "삼성전자 뉴스 확인"),
                        Map.entry("verdict", "DIRECT_ANSWER"),
                        Map.entry("evidence", Map.of("event_type", "REGULATION")),
                        Map.entry("direction", -0.3d),
                        Map.entry("strength", 0.8d),
                        Map.entry("urgency", "watch"),
                        Map.entry("confidence", 0.75d),
                        Map.entry("reasoning_for_judge_agent", "규제성 뉴스가 확인되었습니다."),
                        Map.entry("depth_used", "medium"),
                        Map.entry("focus_tickers", List.of("005930")),
                        Map.entry("tools_called", List.of(Map.of(
                                "tool_name", "news.search",
                                "purpose", "시장 반응 확인",
                                "summary", "뉴스 2건 확인"
                        )))
                )),
                "decision", Map.of(
                        "decision", "USER_DECISION_REQUIRED",
                        "summary", "사용자 확인이 필요합니다.",
                        "confidence", 0.7d,
                        "urgency", "watch",
                        "candidate_rebalance_plan", Map.of("005930", -0.05d),
                        "needs_trade_evaluation", true,
                        "consensus_score", -0.21d,
                        "divergence_score", 0.0d,
                        "trigger", "pull"
                ),
                "knowledge_sources", Map.of("events", "s3://libra/events/latest.json"),
                "runtime", Map.of("engine", "langgraph", "thread_id", "thread-123")
        );

        DecisionRunRecord record = decisionRunService.record(userId, request, portfolio, result);

        assertThat(decisionRunRepository.findById(record.id()))
                .isPresent()
                .get()
                .satisfies(run -> assertThat(run.getUserId()).isEqualTo(userId));
        assertThat(agentSignalRepository.findByDecisionRunIdOrderByTurnNumberAscIdAsc(record.id()))
                .singleElement()
                .satisfies(signal -> {
                    assertThat(signal.getAgentId()).isEqualTo("news");
                    assertThat(signal.getSignalScore()).isEqualByComparingTo(new BigDecimal("-0.12600"));
                });
        assertThat(rebalancePlanItemRepository.findByDecisionRunIdOrderByTickerAsc(record.id()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getTicker()).isEqualTo("005930");
                    assertThat(item.getWeightDelta()).isEqualByComparingTo(new BigDecimal("-0.050000"));
                });
        assertThat(decisionRunService.getDetail(userId, record.id()).candidateRebalancePlan())
                .containsEntry("005930", -0.05d);
    }

    @Test
    void recordsDomainAgentSignalMetadataFromEvidence() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.6d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.4d,
                List.of("초보자")
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "도메인 리스크 점검",
                portfolio,
                Map.of(),
                "medium",
                "pull",
                null,
                null,
                "thread-domain",
                false,
                null,
                null,
                null
        );
        Map<String, Object> result = Map.of(
                "model", "test-model",
                "query", request.query(),
                "portfolio", portfolio,
                "agent_responses", List.of(Map.ofEntries(
                        Map.entry("agent_id", "risk"),
                        Map.entry("opinion_id", "risk-1"),
                        Map.entry("turn_number", 1),
                        Map.entry("verdict", "DIRECT_ANSWER"),
                        Map.entry("evidence", Map.of(
                                "vote", "reject",
                                "llm_used", "claude-sonnet-4-6",
                                "domain_signals_json", "\"[{\\\"label\\\":\\\"HHI\\\",\\\"value\\\":0.42}]\""
                        )),
                        Map.entry("direction", -0.7d),
                        Map.entry("strength", 0.9d),
                        Map.entry("urgency", "watch"),
                        Map.entry("confidence", 0.8d),
                        Map.entry("reasoning_for_judge_agent", "집중 위험이 큽니다."),
                        Map.entry("focus_tickers", List.of("005930")),
                        Map.entry("tools_called", List.of())
                )),
                "decision", Map.of(
                        "decision", "USER_DECISION_REQUIRED",
                        "summary", "사용자 확인이 필요합니다.",
                        "confidence", 0.7d,
                        "urgency", "watch",
                        "candidate_rebalance_plan", Map.of(),
                        "needs_trade_evaluation", false,
                        "consensus_score", -0.21d,
                        "divergence_score", 0.0d,
                        "trigger", "pull"
                ),
                "knowledge_sources", Map.of(),
                "runtime", Map.of("engine", "langgraph", "thread_id", "thread-domain")
        );

        DecisionRunRecord record = decisionRunService.record(userId, request, portfolio, result);

        assertThat(agentSignalRepository.findByDecisionRunIdOrderByTurnNumberAscIdAsc(record.id()))
                .singleElement()
                .satisfies(signal -> {
                    assertThat(signal.getAgentId()).isEqualTo("risk");
                    assertThat(signal.getAgentKind()).isEqualTo("domain");
                    assertThat(signal.getVote()).isEqualTo("reject");
                    assertThat(signal.getLlmUsed()).isEqualTo("claude-sonnet-4-6");
                    assertThat(signal.getDomainSignalsJson()).contains("\"label\":\"HHI\"");
                    assertThat(signal.getDomainSignalsJson()).doesNotStartWith("\"");
                });
        entityManager.flush();
        entityManager.clear();
        assertThat(decisionRunService.getDetail(userId, record.id()).agentSignals())
                .singleElement()
                .satisfies(signal -> {
                    assertThat(signal.agentKind()).isEqualTo("domain");
                    assertThat(signal.vote()).isEqualTo("reject");
                    assertThat(signal.domainSignalsJson()).doesNotStartWith("\"");
                    assertThat(signal.domainSignals()).isInstanceOf(List.class);
                    assertThat((List<?>) signal.domainSignals()).hasSize(1);
                    assertThat(signal.llmUsed()).isEqualTo("claude-sonnet-4-6");
                });
    }

    @Test
    void recordsDecisionEvaluationForStoredRun() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.6d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.4d,
                List.of("초보자")
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                portfolio,
                Map.of("events", "s3://libra/events/latest.json"),
                "medium",
                "pull",
                null,
                null,
                "thread-123",
                false,
                null,
                null,
                null
        );
        Map<String, Object> result = Map.of(
                "model", "test-model",
                "query", request.query(),
                "portfolio", portfolio,
                "agent_responses", List.of(),
                "decision", Map.of(
                        "decision", "REBALANCE",
                        "summary", "리밸런싱 후보",
                        "confidence", 0.7d,
                        "urgency", "watch",
                        "candidate_rebalance_plan", Map.of("005930", -0.05d),
                        "needs_trade_evaluation", true,
                        "consensus_score", -0.21d,
                        "divergence_score", 0.0d,
                        "trigger", "pull"
                ),
                "knowledge_sources", Map.of("events", "s3://libra/events/latest.json"),
                "runtime", Map.of("engine", "langgraph", "thread_id", "thread-123")
        );
        DecisionRunRecord record = decisionRunService.record(userId, request, portfolio, result);
        when(agentGateway.evaluate(any())).thenReturn(Map.of(
                "agent_id", "evaluation",
                "horizon", "1w",
                "verdict", "BLOCKED",
                "direction_accuracy", false,
                "magnitude_error", 3.5d,
                "cost_efficiency", 0.1d,
                "note", "테스트 평가",
                "metrics", Map.of(
                        "realized_return_pct", -3.5d,
                        "cost_pct", 0.1d
                )
        ));

        DecisionEvaluationResult evaluation = decisionRunService.evaluate(
                userId,
                record.id(),
                new DecisionEvaluationRequest("1w", -3.5d, 0.1d, "rejected: 테스트")
        );

        assertThat(evaluation.decisionRunId()).isEqualTo(record.id());
        assertThat(evaluation.horizon()).isEqualTo("1w");
        assertThat(evaluation.verdict()).isEqualTo("BLOCKED");
        assertThat(decisionEvaluationRepository.findByDecisionRunIdOrderByEvaluatedAtDesc(record.id()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getHorizon()).isEqualTo("1w");
                    assertThat(saved.getVerdict()).isEqualTo("BLOCKED");
                });
    }

    @Test
    void recordsDemoKisExecutionForStoredRun() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.6d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.4d,
                List.of("초보자")
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "리밸런싱 실행 후보",
                portfolio,
                Map.of(),
                "medium",
                "pull",
                null,
                null,
                "thread-order",
                false,
                null,
                null,
                null
        );
        Map<String, Object> result = Map.of(
                "model", "test-model",
                "query", request.query(),
                "portfolio", portfolio,
                "agent_responses", List.of(),
                "decision", Map.of(
                        "decision", "REBALANCE",
                        "summary", "삼성전자 비중 축소",
                        "confidence", 0.8d,
                        "urgency", "scheduled",
                        "called_agents", List.of("profit", "cost"),
                        "candidate_rebalance_plan", Map.of("005930", -0.05d),
                        "needs_trade_evaluation", true,
                        "consensus_score", 0.2d,
                        "divergence_score", 0.0d,
                        "trigger", "pull"
                ),
                "knowledge_sources", Map.of(),
                "runtime", Map.of("engine", "langgraph", "thread_id", "thread-order")
        );
        DecisionRunRecord record = decisionRunService.record(userId, request, portfolio, result);
        kisCredentialService.save(userId, new KisCredentialRequest(
                "demo",
                "demo-app-key",
                "demo-app-secret",
                "12345678-01",
                "01",
                null
        ));
        when(kisCashOrderService.placeDomesticCashOrder(any(), any())).thenReturn(new KisCashOrderResult(
                "005930",
                "SELL",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "ACCEPTED",
                "모의 주문 접수",
                "0000123456",
                "093001",
                Map.of("rt_cd", "0", "msg1", "모의 주문 접수")
        ));

        List<DecisionExecutionResult> executions = decisionRunService.executeKisDemoOrders(
                userId,
                record.id(),
                new DecisionExecutionRequest(List.of(new DecisionExecutionOrderItem(
                        "005930",
                        "SELL",
                        1L,
                        BigDecimal.ZERO,
                        "01"
                )), false)
        );

        assertThat(executions)
                .singleElement()
                .satisfies(execution -> {
                    assertThat(execution.status()).isEqualTo("ACCEPTED");
                    assertThat(execution.orderNo()).isEqualTo("0000123456");
                    assertThat(execution.message()).isEqualTo("모의 주문 접수");
                });
        assertThat(decisionExecutionRepository.findByDecisionRunIdOrderByCreatedAtDesc(record.id()))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getTicker()).isEqualTo("005930");
                    assertThat(saved.getSide()).isEqualTo("SELL");
                    assertThat(saved.getStatus()).isEqualTo("ACCEPTED");
                });
        assertThat(decisionRunService.getDetail(userId, record.id()).executions())
                .singleElement()
                .satisfies(execution -> assertThat(execution.orderNo()).isEqualTo("0000123456"));
    }

    @Test
    void proposesDemoKisExecutionOrdersFromCandidatePlanAndKisQuotes() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.4d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.6d,
                List.of("초보자")
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "리밸런싱 주문 제안",
                portfolio,
                Map.of(),
                "medium",
                "pull",
                null,
                null,
                "thread-proposal",
                false,
                null,
                null,
                null
        );
        Map<String, Object> result = Map.of(
                "model", "test-model",
                "query", request.query(),
                "portfolio", portfolio,
                "agent_responses", List.of(),
                "decision", Map.of(
                        "decision", "REBALANCE",
                        "summary", "삼성전자 비중 확대",
                        "confidence", 0.8d,
                        "urgency", "scheduled",
                        "called_agents", List.of("profit", "cost"),
                        "candidate_rebalance_plan", Map.of("005930", 0.10d),
                        "needs_trade_evaluation", true,
                        "consensus_score", 0.2d,
                        "divergence_score", 0.0d,
                        "trigger", "pull"
                ),
                "knowledge_sources", Map.of(),
                "runtime", Map.of("engine", "langgraph", "thread_id", "thread-proposal")
        );
        DecisionRunRecord record = decisionRunService.record(userId, request, portfolio, result);
        kisCredentialService.save(userId, new KisCredentialRequest(
                "demo",
                "demo-app-key",
                "demo-app-secret",
                "12345678-01",
                "01",
                null
        ));
        when(kisMarketPriceService.fetchDomesticQuotes(any(), any(), any())).thenReturn(List.of(new KisQuote(
                "005930",
                "삼성전자",
                65000d,
                0d,
                0d,
                1000L,
                OffsetDateTime.parse("2026-04-15T10:01:00+09:00"),
                "KIS_DOMESTIC_PRICE"
        )));

        List<DecisionExecutionProposalItem> proposals = decisionRunService.proposeKisDemoOrders(userId, record.id());

        assertThat(proposals)
                .singleElement()
                .satisfies(proposal -> {
                    assertThat(proposal.ticker()).isEqualTo("005930");
                    assertThat(proposal.side()).isEqualTo("BUY");
                    assertThat(proposal.quantity()).isEqualTo(1L);
                    assertThat(proposal.priceKrw()).isEqualByComparingTo(new BigDecimal("65000"));
                    assertThat(proposal.amountKrw()).isEqualByComparingTo(new BigDecimal("65000"));
                    assertThat(proposal.orderType()).isEqualTo("01");
                    assertThat(proposal.weightDelta()).isEqualTo(0.10d);
                });
    }

    @Test
    void rejectsDecisionRunAccessFromDifferentUser() {
        UserEntity intruder = userRepository.save(UserEntity.newLocalUser(
                "intruder@example.com",
                "$2a$10$dummy.hash.for.test.purposes.only0000000000000000000",
                "Intruder"
        ));
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.6d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.4d,
                List.of()
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                portfolio,
                Map.of(),
                "medium",
                "pull",
                null,
                null,
                "thread-xyz",
                false,
                null,
                null,
                null
        );
        Map<String, Object> result = Map.of(
                "model", "test-model",
                "query", request.query(),
                "portfolio", portfolio,
                "agent_responses", List.of(),
                "decision", Map.of(
                        "decision", "DEFER",
                        "summary", "보류",
                        "confidence", 0.5d,
                        "urgency", "defer",
                        "needs_trade_evaluation", false,
                        "consensus_score", 0.0d,
                        "divergence_score", 0.0d,
                        "trigger", "pull"
                ),
                "knowledge_sources", Map.of(),
                "runtime", Map.of("engine", "langgraph", "thread_id", "thread-xyz")
        );
        DecisionRunRecord record = decisionRunService.record(userId, request, portfolio, result);

        assertThatThrownBy(() -> decisionRunService.getDetail(intruder.id(), record.id()))
                .isInstanceOf(DecisionRunService.DecisionRunNotFoundException.class);
    }
}
