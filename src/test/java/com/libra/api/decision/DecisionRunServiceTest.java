package com.libra.api.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "libra.agent.base-url=http://127.0.0.1:1",
        "libra.agent.connect-timeout-ms=100",
        "libra.agent.read-timeout-ms=100",
        "libra.agent.fallback-to-stub=true"
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
                false
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

        DecisionRunRecord record = decisionRunService.record(request, portfolio, result);

        assertThat(decisionRunRepository.findById(record.id())).isPresent();
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
        assertThat(decisionRunService.getDetail(record.id()).candidateRebalancePlan())
                .containsEntry("005930", -0.05d);
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
                false
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
        DecisionRunRecord record = decisionRunService.record(request, portfolio, result);

        DecisionEvaluationResult evaluation = decisionRunService.evaluate(
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
}
