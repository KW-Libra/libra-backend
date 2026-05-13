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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentSseClient {

    private static final Logger log = LoggerFactory.getLogger(AgentSseClient.class);

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final ExecutorService relayExecutor;

    public AgentSseClient(AgentProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(props.connectTimeout())
            .build();
        this.relayExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public SseEmitter startRun(Object body, User user, String traceId) {
        return openAndRelay("/api/runs", body, user, traceId);
    }

    public SseEmitter resumeRun(String threadId, Object body, User user, String traceId) {
        String path = "/api/runs/" + encodePathSegment(threadId) + "/resume";
        return openAndRelay(path, body, user, traceId);
    }

    @PreDestroy
    public void shutdown() {
        relayExecutor.shutdownNow();
    }

    private SseEmitter openAndRelay(String path, Object body, User user, String traceId) {
        HttpResponse<InputStream> response = openAgentStream(path, body, user, traceId);
        SseEmitter emitter = new SseEmitter(props.streamTimeout().toMillis());
        relayExecutor.execute(() -> relay(response.body(), emitter, traceId));
        return emitter;
    }

    private HttpResponse<InputStream> openAgentStream(String path, Object body, User user, String traceId) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(props.endpoint(path))
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

    private void relay(InputStream stream, SseEmitter emitter, String traceId) {
        emitter.onTimeout(() -> closeQuietly(stream));
        emitter.onCompletion(() -> closeQuietly(stream));
        emitter.onError(_error -> closeQuietly(stream));

        try (stream; BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String eventName = "message";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    if (data.length() > 0) {
                        send(emitter, eventName, data.toString());
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
                send(emitter, eventName, data.toString());
            }
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.warn("agent SSE relay closed traceId={} message={}", traceId, e.getMessage());
            emitter.complete();
        } catch (Exception e) {
            log.error("agent SSE relay failed traceId={}", traceId, e);
            emitter.completeWithError(e);
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
