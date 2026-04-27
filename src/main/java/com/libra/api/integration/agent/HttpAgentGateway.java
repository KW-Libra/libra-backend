package com.libra.api.integration.agent;

import com.libra.api.judge.JudgeRunDispatchRequest;
import com.libra.api.knowledge.KnowledgeSourceResolver;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAgentGateway implements AgentGateway {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final AgentProperties properties;
    private final StubAgentGateway stubAgentGateway;
    private final KnowledgeSourceResolver knowledgeSourceResolver;
    private final RestClient restClient;

    public HttpAgentGateway(
            RestClient.Builder restClientBuilder,
            AgentProperties properties,
            StubAgentGateway stubAgentGateway,
            KnowledgeSourceResolver knowledgeSourceResolver
    ) {
        this.properties = properties;
        this.stubAgentGateway = stubAgentGateway;
        this.knowledgeSourceResolver = knowledgeSourceResolver;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Override
    public Map<String, Object> run(JudgeRunDispatchRequest request, PortfolioSnapshot portfolio) {
        Map<String, Object> payload = buildPayload(request, portfolio);
        try {
            Map<String, Object> result = restClient.post()
                    .uri("/v1/judge-runs")
                    .body(payload)
                    .retrieve()
                    .body(MAP_RESPONSE);
            if (result == null || result.isEmpty()) {
                throw new AgentGatewayException("libra-agent returned an empty response.");
            }
            result.putIfAbsent("state_record", null);
            return result;
        } catch (RestClientException | AgentGatewayException exception) {
            if (!properties.fallbackToStub()) {
                throw new AgentGatewayException("Failed to call libra-agent at " + properties.baseUrl(), exception);
            }
            return stubAgentGateway.runWithReason(
                    request,
                    portfolio,
                    "HTTP call to libra-agent failed: " + exception.getMessage()
            );
        }
    }

    @Override
    public Map<String, Object> evaluate(Map<String, Object> payload) {
        try {
            Map<String, Object> result = restClient.post()
                    .uri("/v1/evaluations")
                    .body(payload)
                    .retrieve()
                    .body(MAP_RESPONSE);
            if (result == null || result.isEmpty()) {
                throw new AgentGatewayException("libra-agent returned an empty evaluation response.");
            }
            return result;
        } catch (RestClientException | AgentGatewayException exception) {
            if (!properties.fallbackToStub()) {
                throw new AgentGatewayException("Failed to call libra-agent evaluation endpoint at " + properties.baseUrl(), exception);
            }
            return stubAgentGateway.evaluateWithReason(
                    payload,
                    "HTTP call to libra-agent evaluation endpoint failed: " + exception.getMessage()
            );
        }
    }

    Map<String, Object> buildPayload(JudgeRunDispatchRequest request, PortfolioSnapshot portfolio) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", request.query());
        payload.put("portfolio", portfolio);
        Map<String, String> knowledgeSources = knowledgeSourceResolver.resolve(request);
        if (!CollectionUtils.isEmpty(knowledgeSources)) {
            payload.put("knowledge_sources", knowledgeSources);
        } else {
            payload.put("knowledge_base", Map.of(
                    "events", java.util.List.of(),
                    "documents", java.util.List.of(),
                    "source_paths", Map.of()
            ));
        }
        if (StringUtils.hasText(request.depth())) {
            payload.put("depth", request.depth());
        }
        if (StringUtils.hasText(request.trigger())) {
            payload.put("trigger", request.trigger());
        }
        if (request.triggerEvent() != null) {
            payload.put("trigger_event", request.triggerEvent());
        }
        if (request.deadlineSeconds() != null) {
            payload.put("deadline_seconds", request.deadlineSeconds());
        }
        if (StringUtils.hasText(request.threadId())) {
            payload.put("thread_id", request.threadId());
        }
        if (request.enableHumanInterrupts() != null) {
            payload.put("enable_human_interrupts", request.enableHumanInterrupts());
        }
        return payload;
    }

    private SimpleClientHttpRequestFactory requestFactory(AgentProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return factory;
    }
}
