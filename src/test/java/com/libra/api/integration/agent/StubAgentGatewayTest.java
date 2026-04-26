package com.libra.api.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StubAgentGatewayTest {

    private final StubAgentGateway gateway = new StubAgentGateway();

    @Test
    void returnsContractShapedStubResult() {
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.5d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.5d,
                List.of("보수적")
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                portfolio,
                Map.of("events", "D:/libra-ingest/out/events.json"),
                "medium",
                "pull",
                null,
                null,
                "thread-123",
                false
        );

        Map<String, Object> result = gateway.run(request, portfolio);

        assertThat(result).containsKeys("model", "query", "portfolio", "agent_responses", "decision", "knowledge_sources", "runtime");
        assertThat(result.get("model")).isEqualTo("libra-backend-stub");
        assertThat(result.get("query")).isEqualTo("포트폴리오 점검");
        assertThat(result.get("knowledge_sources")).isEqualTo(Map.of("events", "D:/libra-ingest/out/events.json"));

        Map<String, Object> decision = cast(result.get("decision"));
        assertThat(decision.get("decision")).isEqualTo("DEFER");
        assertThat(decision.get("urgency")).isEqualTo("defer");
        assertThat(decision.get("needs_trade_evaluation")).isEqualTo(false);
        assertThat(castList(decision.get("decision_trace"))).hasSize(1);

        Map<String, Object> runtime = cast(result.get("runtime"));
        assertThat(runtime.get("engine")).isEqualTo("spring-boot-stub");
        assertThat(runtime.get("thread_id")).isEqualTo("thread-123");
        assertThat(runtime.get("interrupted")).isEqualTo(false);
        assertThat(runtime.get("resume_required")).isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) {
        return (List<Object>) value;
    }
}
