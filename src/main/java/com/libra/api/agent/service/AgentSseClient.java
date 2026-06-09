package com.libra.api.agent.service;

import com.libra.api.agent.config.AgentProperties;
import com.libra.api.auth.domain.User;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentSseClient {

    private static final Logger log = LoggerFactory.getLogger(AgentSseClient.class);

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final AgentRunService agentRuns;
    private final HttpClient http;
    private final ExecutorService relayExecutor;

    public AgentSseClient(AgentProperties props, ObjectMapper objectMapper, AgentRunService agentRuns) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.agentRuns = agentRuns;
        this.http = HttpClient.newBuilder()
            .connectTimeout(props.connectTimeout())
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.relayExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SseEmitter startRun(Object body, User user, String traceId) {
        return openAndRelay("/api/runs", body, user, traceId, null);
    }

    /**
     * prepare(데이터 수집)를 SSE 스트림 안에서 비동기로 실행한다. emitter 를 즉시 열어
     * {@code run_preparing} 이벤트와 주기적 keepalive 코멘트를 보내며(프록시 타임아웃 방지),
     * 준비가 끝나면 agent 스트림을 그대로 중계한다. 준비 단계 실패는 {@code run_failed} 로 보낸다.
     * runId 가 주어지면 중계하는 이벤트를 History 로 영속화한다.
     */
    public SseEmitter startRunWithPreparation(Callable<Object> preparation, User user, String traceId, UUID runId) {
        SseEmitter emitter = new SseEmitter(props.streamTimeout().toMillis());
        relayExecutor.execute(() -> runWithPreparation(preparation, emitter, user, traceId, runId));
        return emitter;
    }

    private void runWithPreparation(Callable<Object> preparation, SseEmitter emitter, User user, String traceId, UUID runId) {
        AtomicBoolean clientGone = new AtomicBoolean(false);
        emitter.onTimeout(() -> clientGone.set(true));
        emitter.onError(_error -> clientGone.set(true));
        emitter.onCompletion(() -> clientGone.set(true));

        Future<Object> task = relayExecutor.submit(preparation);
        Object prepared;
        try {
            sendNamed(emitter, "run_preparing", objectMapper.writeValueAsString(Map.of(
                "phase", "prepare",
                "message", "시장 데이터·뉴스·공시 수집 중")));
            while (true) {
                if (clientGone.get()) {
                    task.cancel(true);
                    return;
                }
                try {
                    prepared = task.get(15, TimeUnit.SECONDS);
                    break;
                } catch (TimeoutException te) {
                    emitter.send(SseEmitter.event().comment("preparing"));
                }
            }
        } catch (ExecutionException ee) {
            task.cancel(true);
            Throwable cause = ee.getCause();
            ApiException api = (cause instanceof ApiException a)
                ? a
                : new ApiException(ErrorCode.INTERNAL_ERROR, "데이터 준비 중 오류가 발생했습니다", cause);
            failRunQuietly(runId, 0);
            sendRunFailed(emitter, traceId, "prepare", api);
            return;
        } catch (InterruptedException ie) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            failRunQuietly(runId, 0);
            emitter.completeWithError(ie);
            return;
        } catch (IOException | IllegalStateException io) {
            task.cancel(true);
            log.warn("preparing stream closed traceId={} message={}", traceId, io.getMessage());
            return;
        }

        try {
            HttpResponse<InputStream> response = openAgentStream("/api/runs", prepared, user, traceId);
            relay(response.body(), emitter, traceId, runId);
        } catch (ApiException api) {
            failRunQuietly(runId, 0);
            sendRunFailed(emitter, traceId, "open_agent", api);
        }
    }

    private void sendNamed(SseEmitter emitter, String name, String json) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(json));
    }

    private void sendRunFailed(SseEmitter emitter, String traceId, String phase, ApiException error) {
        try {
            String message = error.getMessage() == null ? error.getCode().name() : error.getMessage();
            emitter.send(SseEmitter.event().name("run_failed").data(objectMapper.writeValueAsString(Map.of(
                "thread_id", traceId,
                "code", error.getCode().name(),
                "error", message,
                "phase", phase,
                "traceId", traceId))));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * SSE 를 소비할 클라이언트가 없는 호출 경로(스케줄러 등)용. agent 스트림을 열어 끝까지
     * 소비하고 마지막 이벤트 요약을 반환한다.
     */
    public ScheduledRunOutcome runToCompletion(Object body, User user, String traceId) {
        HttpResponse<InputStream> response = openAgentStream("/api/runs", body, user, traceId);
        return drain(response.body(), traceId);
    }

    private ScheduledRunOutcome drain(InputStream stream, String traceId) {
        int events = 0;
        String lastEvent = null;
        String lastData = null;
        try (stream; BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String eventName = "message";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (data.length() > 0) {
                        events++;
                        lastEvent = eventName;
                        lastData = data.toString();
                        eventName = "message";
                        data.setLength(0);
                    }
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(stripOneLeadingSpace(line.substring("data:".length())));
                }
            }
            if (data.length() > 0) {
                events++;
                lastEvent = eventName;
                lastData = data.toString();
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("agent run drain closed traceId={} message={}", traceId, e.getMessage());
        }
        return new ScheduledRunOutcome(events, lastEvent, snippet(lastData));
    }

    private static String snippet(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    /** SSE 미소비 run 의 결과 요약(이벤트 수와 마지막 이벤트). */
    public record ScheduledRunOutcome(int eventCount, String lastEvent, String lastData) {
    }

    public SseEmitter resumeRun(String threadId, Object body, User user, String traceId) {
        String path = "/api/runs/" + encodePathSegment(threadId) + "/resume";
        UUID runId = findRunIdQuietly(user, threadId);
        return openAndRelay(path, body, user, traceId, runId);
    }

    @PreDestroy
    public void shutdown() {
        relayExecutor.shutdownNow();
    }

    private SseEmitter openAndRelay(String path, Object body, User user, String traceId, UUID runId) {
        HttpResponse<InputStream> response = openAgentStream(path, body, user, traceId);
        SseEmitter emitter = new SseEmitter(props.streamTimeout().toMillis());
        relayExecutor.execute(() -> relay(response.body(), emitter, traceId, runId));
        return emitter;
    }

    private HttpResponse<InputStream> openAgentStream(String path, Object body, User user, String traceId) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(props.endpoint(path))
                .version(HttpClient.Version.HTTP_1_1)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("X-Trace-Id", traceId)
                .header("X-Libra-User-Id", user.getId().toString())
                .header("X-Libra-User-Email", user.getEmail())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "에이전트 요청을 직렬화할 수 없습니다", e);
        }

        try {
            HttpResponse<InputStream> response = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                    ErrorCode.AGENT_UNAVAILABLE,
                    "에이전트가 오류를 반환했습니다: " + response.statusCode() + " " + readBody(response.body())
                );
            }
            return response;
        } catch (ConnectException e) {
            throw new ApiException(ErrorCode.AGENT_UNAVAILABLE, ErrorCode.AGENT_UNAVAILABLE.defaultMessage(), e);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.AGENT_UNAVAILABLE, ErrorCode.AGENT_UNAVAILABLE.defaultMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.AGENT_TIMEOUT, ErrorCode.AGENT_TIMEOUT.defaultMessage(), e);
        }
    }

    private void relay(InputStream stream, SseEmitter emitter, String traceId, UUID runId) {
        emitter.onTimeout(() -> closeQuietly(stream));
        emitter.onCompletion(() -> closeQuietly(stream));
        emitter.onError(_error -> closeQuietly(stream));

        int index = 0;
        String lastEvent = null;
        String lastData = null;
        try (stream; BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String eventName = "message";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (data.length() > 0) {
                        String payload = data.toString();
                        send(emitter, eventName, payload);
                        persistEvent(runId, index++, eventName, payload);
                        lastEvent = eventName;
                        lastData = payload;
                        eventName = "message";
                        data.setLength(0);
                    }
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(stripOneLeadingSpace(line.substring("data:".length())));
                }
            }
            if (data.length() > 0) {
                String payload = data.toString();
                send(emitter, eventName, payload);
                persistEvent(runId, index++, eventName, payload);
                lastEvent = eventName;
                lastData = payload;
            }
            finalizeRun(runId, lastEvent, lastData, index);
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.warn("agent SSE relay closed traceId={} message={}", traceId, e.getMessage());
            finalizeRun(runId, lastEvent, lastData, index);
            emitter.complete();
        } catch (Exception e) {
            log.error("agent SSE relay failed traceId={}", traceId, e);
            finalizeRun(runId, lastEvent, lastData, index);
            emitter.completeWithError(e);
        }
    }

    private void persistEvent(UUID runId, int index, String eventName, String payload) {
        if (runId == null || eventName == null) {
            return;
        }
        try {
            agentRuns.recordEvent(runId, index, eventName, payload);
            if ("run_started".equals(eventName)) {
                String threadId = readField(payload, "thread_id");
                if (threadId != null) {
                    agentRuns.attachThread(runId, threadId);
                }
            }
        } catch (RuntimeException e) {
            log.warn("failed to persist run event runId={} type={} msg={}", runId, eventName, e.getMessage());
        }
    }

    private void finalizeRun(UUID runId, String lastEvent, String lastData, int count) {
        if (runId == null) {
            return;
        }
        try {
            if ("run_completed".equals(lastEvent)) {
                agentRuns.completeRun(runId, readField(lastData, "decision"), readField(lastData, "branch"), null, count);
            } else if ("run_failed".equals(lastEvent)) {
                agentRuns.failRun(runId, count);
            }
            // interrupt_required / 비정상 종료 시 RUNNING 유지 — resume 으로 이어진다.
        } catch (RuntimeException e) {
            log.warn("failed to finalize run runId={} msg={}", runId, e.getMessage());
        }
    }

    private void failRunQuietly(UUID runId, int count) {
        if (runId == null) {
            return;
        }
        try {
            agentRuns.failRun(runId, count);
        } catch (RuntimeException e) {
            log.warn("failed to mark run failed runId={} msg={}", runId, e.getMessage());
        }
    }

    private UUID findRunIdQuietly(User user, String threadId) {
        try {
            return agentRuns.findRunIdByThread(user.getId(), threadId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String readField(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node == null || node.isNull() ? null : node.asText();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void send(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private static String stripOneLeadingSpace(String value) {
        if (value.startsWith(" ")) {
            return value.substring(1);
        }
        return value;
    }

    private static String readBody(InputStream body) {
        try (body) {
            String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            return text.length() > 500 ? text.substring(0, 500) + "..." : text;
        } catch (IOException e) {
            return "(body read failed)";
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
