package com.libra.api.integration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.portfolio.PortfolioDefinition;
import com.libra.api.knowledge.KnowledgeProperties;
import com.libra.api.knowledge.KnowledgeSourceResolver;
import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import com.libra.api.portfolio.TargetWeight;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpAgentGatewayTest {

    @Test
    void postsJudgeRunToAgentApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startAgentStub(requestBody);
        try {
            AgentProperties properties = new AgentProperties(
                    "http://localhost:" + server.getAddress().getPort(),
                    1000,
                    1000
            );
            HttpAgentGateway gateway = new HttpAgentGateway(
                    RestClient.builder(),
                    properties,
                    knowledgeResolver("", 240)
            );

            Map<String, Object> result = gateway.run(request(), portfolio());

            assertThat(result.get("model")).isEqualTo("remote-agent");
            assertThat(requestBody.get()).contains("\"query\":\"포트폴리오 점검\"");
            assertThat(requestBody.get()).contains("\"knowledge_base\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsWhenAgentApiIsUnavailable() {
        AgentProperties properties = new AgentProperties(
                "http://127.0.0.1:1",
                100,
                100
        );
        HttpAgentGateway gateway = new HttpAgentGateway(
                RestClient.builder(),
                properties,
                knowledgeResolver("", 240)
        );

        assertThatThrownBy(() -> gateway.run(request(), portfolio()))
                .isInstanceOf(AgentGatewayException.class)
                .hasMessageContaining("Failed to call libra-agent");
    }

    @Test
    void postsEvaluationToAgentApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startAgentStub(requestBody);
        try {
            AgentProperties properties = new AgentProperties(
                    "http://localhost:" + server.getAddress().getPort(),
                    1000,
                    1000
            );
            HttpAgentGateway gateway = new HttpAgentGateway(
                    RestClient.builder(),
                    properties,
                    knowledgeResolver("", 240)
            );

            Map<String, Object> result = gateway.evaluate(Map.of(
                    "horizon", "1w",
                    "realized_return_pct", -3.5d,
                    "cost_pct", 0.1d,
                    "decision_run_result", Map.of("decision", Map.of("decision", "REBALANCE"))
            ));

            assertThat(result.get("agent_id")).isEqualTo("evaluation");
            assertThat(result.get("verdict")).isEqualTo("FALSE_POSITIVE");
            assertThat(requestBody.get()).contains("\"realized_return_pct\":-3.5");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesConfiguredLocalKnowledgeSourcesWhenRequestDoesNotProvideThem() throws Exception {
        Path knowledgeDir = Files.createTempDirectory("libra-knowledge");
        Files.writeString(knowledgeDir.resolve("events.json"), "{\"events\":[]}", StandardCharsets.UTF_8);
        Files.writeString(knowledgeDir.resolve("normalized_documents.json"), "{\"documents\":[]}", StandardCharsets.UTF_8);
        Files.writeString(
                knowledgeDir.resolve("manifest.json"),
                "{\"generated_at\":\"" + OffsetDateTime.now() + "\"}",
                StandardCharsets.UTF_8
        );
        AgentProperties properties = new AgentProperties("http://localhost:8010", 1000, 1000);
        HttpAgentGateway gateway = new HttpAgentGateway(
                RestClient.builder(),
                properties,
                knowledgeResolver(knowledgeDir.toString(), 240)
        );

        Map<String, Object> payload = gateway.buildPayload(request(), portfolio(), java.util.List.of());

        assertThat(cast(payload.get("knowledge_sources")))
                .containsEntry("events", knowledgeDir.resolve("events.json").toAbsolutePath().normalize().toString())
                .containsEntry(
                        "normalized_documents",
                        knowledgeDir.resolve("normalized_documents.json").toAbsolutePath().normalize().toString()
                );
        assertThat(payload).doesNotContainKey("knowledge_base");
    }

    @Test
    void forwardsIngestRefreshOptionsToAgentApi() {
        AgentProperties properties = new AgentProperties("http://localhost:8010", 1000, 1000);
        HttpAgentGateway gateway = new HttpAgentGateway(
                RestClient.builder(),
                properties,
                knowledgeResolver("", 240)
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                null,
                null,
                "medium",
                "pull",
                null,
                null,
                "thread-123",
                false,
                true,
                Map.of(
                        "mode", "live",
                        "root", "D:/libra-ingest",
                        "rss_limit", 1,
                        "dart_limit", 0,
                        "report_limit", 0
                ),
                null
        );

        Map<String, Object> payload = gateway.buildPayload(request, portfolio(), java.util.List.of());

        assertThat(payload).containsEntry("allow_ingest_refresh", true);
        assertThat(cast(payload.get("ingest_refresh")))
                .containsEntry("mode", "live")
                .containsEntry("root", "D:/libra-ingest")
                .containsEntry("rss_limit", 1)
                .containsEntry("dart_limit", 0)
                .containsEntry("report_limit", 0);
    }

    @Test
    void forwardsPortfolioDefinitionToAgentApi() {
        AgentProperties properties = new AgentProperties("http://localhost:8010", 1000, 1000);
        HttpAgentGateway gateway = new HttpAgentGateway(
                RestClient.builder(),
                properties,
                knowledgeResolver("", 240)
        );
        JudgeRunDispatchRequest request = new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                null,
                null,
                "medium",
                "pull",
                null,
                null,
                "thread-123",
                false,
                null,
                null,
                new PortfolioDefinition(
                        "반도체 집중",
                        "사용자 정의 인덱스",
                        List.of(new TargetWeight("005930", "삼성전자", 1.0d, "KR")),
                        "위험중립형",
                        0.05d,
                        "임계치 도달 시",
                        false,
                        OffsetDateTime.parse("2026-05-07T00:00:00+09:00")
                )
        );

        Map<String, Object> payload = gateway.buildPayload(request, portfolio(), java.util.List.of());

        assertThat(payload.get("portfolio_definition"))
                .isInstanceOfSatisfying(
                        PortfolioDefinition.class,
                        definition -> assertThat(definition.name()).isEqualTo("반도체 집중")
                );
    }

    private HttpServer startAgentStub(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/judge-runs", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "model": "remote-agent",
                      "query": "포트폴리오 점검",
                      "portfolio": {},
                      "agent_responses": [],
                      "decision": {
                        "decision": "HOLD",
                        "summary": "보유 유지",
                        "confidence": 0.5,
                        "urgency": "defer",
                        "decision_trace": [],
                        "candidate_rebalance_plan": {},
                        "needs_trade_evaluation": false,
                        "consensus_score": 0.0,
                        "divergence_score": 0.0,
                        "trigger": "pull"
                      },
                      "knowledge_sources": {},
                      "runtime": {
                        "engine": "langgraph",
                        "thread_id": "thread-remote",
                        "checkpoint_path": null,
                        "interrupted": false,
                        "resume_required": false
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/v1/evaluations", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "agent_id": "evaluation",
                      "horizon": "1w",
                      "verdict": "FALSE_POSITIVE",
                      "direction_accuracy": false,
                      "magnitude_error": 3.5,
                      "cost_efficiency": 0.1,
                      "note": "테스트 평가",
                      "metrics": {
                        "realized_return_pct": -3.5,
                        "cost_pct": 0.1
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private JudgeRunDispatchRequest request() {
        return new JudgeRunDispatchRequest(
                "포트폴리오 점검",
                null,
                null,
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
    }

    private KnowledgeSourceResolver knowledgeResolver(String localDir, int maxAgeMinutes) {
        return new KnowledgeSourceResolver(new KnowledgeProperties(localDir, maxAgeMinutes), new ObjectMapper());
    }

    private PortfolioSnapshot portfolio() {
        return new PortfolioSnapshot(
                OffsetDateTime.parse("2026-04-15T10:00:00+09:00"),
                List.of(new PortfolioHolding("005930", "삼성전자", 0.5d, List.of("005930.KS"), 10d, 65000d)),
                1000000d,
                0.5d,
                List.of("보수적")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
