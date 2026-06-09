package com.libra.api.scheduler.service;

import com.libra.api.agent.api.dto.RunStartRequest;
import com.libra.api.agent.service.AgentSseClient;
import com.libra.api.auth.domain.User;
import com.libra.api.auth.domain.UserRepository;
import com.libra.api.ingest.service.LiveIngestService;
import com.libra.api.portfolio.domain.PortfolioSnapshot;
import com.libra.api.portfolio.domain.PortfolioSnapshotRepository;
import com.libra.api.scheduler.config.SchedulerProperties;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 매일 정해진 시각에 활성 사용자의 최신 포트폴리오 스냅샷으로 Judge run 을 자동 트리거한다.
 *
 * <p>수동 run({@code POST /api/runs}) 과 동일한 경로(LiveIngestService.prepare → AgentSseClient)
 * 를 재사용하되, 소비할 SSE 클라이언트가 없으므로 스트림을 내부적으로 끝까지 소비한다.
 * 한 사용자의 실패가 다른 사용자 처리를 막지 않도록 사용자 단위로 예외를 격리한다.
 *
 * <p>{@code libra.scheduler.enabled=true} 일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "libra.scheduler", name = "enabled", havingValue = "true")
public class DailyRebalanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyRebalanceScheduler.class);
    private static final DateTimeFormatter BATCH_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SchedulerProperties props;
    private final UserRepository users;
    private final PortfolioSnapshotRepository snapshots;
    private final LiveIngestService ingest;
    private final AgentSseClient agent;
    private final ObjectMapper objectMapper;

    public DailyRebalanceScheduler(
        SchedulerProperties props,
        UserRepository users,
        PortfolioSnapshotRepository snapshots,
        LiveIngestService ingest,
        AgentSseClient agent,
        ObjectMapper objectMapper
    ) {
        this.props = props;
        this.users = users;
        this.snapshots = snapshots;
        this.ingest = ingest;
        this.agent = agent;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
        cron = "${libra.scheduler.daily-cron:0 30 8 * * MON-FRI}",
        zone = "${libra.scheduler.zone:Asia/Seoul}"
    )
    public void runDailyRebalance() {
        runForAllActiveUsers();
    }

    /**
     * 활성 사용자 전원을 순회하며 일일 리밸런싱 판단 run 을 트리거한다.
     * 스케줄 외 수동 실행/테스트를 위해 public 으로 노출한다.
     */
    public SweepResult runForAllActiveUsers() {
        String batchId = "sched-" + BATCH_STAMP.format(OffsetDateTime.now(zoneId()));
        List<User> active = users.findByActiveTrue();
        log.info("scheduled daily rebalance start batch={} activeUsers={}", batchId, active.size());

        int triggered = 0;
        int skipped = 0;
        int failed = 0;
        for (User user : active) {
            UUID userId = user.getId();
            try {
                Optional<PortfolioSnapshot> latest = snapshots.findFirstByUserIdOrderByCreatedAtDesc(userId);
                if (latest.isEmpty()) {
                    skipped++;
                    log.info("scheduled rebalance skip user={} reason=no_snapshot batch={}", userId, batchId);
                    continue;
                }
                Map<String, Object> portfolio = parsePortfolio(latest.get().getSnapshotJson());
                String traceId = batchId + "-" + shortId(userId);
                RunStartRequest request = buildRequest(portfolio, traceId);
                RunStartRequest prepared = ingest.prepare(request, user, traceId);
                AgentSseClient.ScheduledRunOutcome outcome = agent.runToCompletion(prepared, user, traceId);
                triggered++;
                log.info("scheduled rebalance done user={} events={} lastEvent={} batch={}",
                    userId, outcome.eventCount(), outcome.lastEvent(), batchId);
            } catch (Exception e) {
                failed++;
                log.warn("scheduled rebalance failed user={} batch={} error={}",
                    userId, batchId, e.getMessage());
            }
        }

        log.info("scheduled daily rebalance end batch={} triggered={} skipped={} failed={}",
            batchId, triggered, skipped, failed);
        return new SweepResult(active.size(), triggered, skipped, failed);
    }

    private RunStartRequest buildRequest(Map<String, Object> portfolio, String traceId) {
        Map<String, Object> triggerEvent = Map.of(
            "type", "scheduled_daily",
            "source", "backend-scheduler"
        );
        return new RunStartRequest(
            props.query(),
            portfolio,
            null,                       // knowledge_base
            null,                       // ingest_bundle — prepare() 가 채움
            null,                       // knowledge_sources
            null,                       // portfolio_definition
            triggerEvent,
            null,                       // governance_v1
            "pull",
            props.depth(),
            props.deadlineSeconds(),
            truncate(traceId),          // thread_id (max 120)
            props.approvalRequired(),
            null                        // enable_human_interrupts — approval_required 기본 따름
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePortfolio(String snapshotJson) {
        return objectMapper.readValue(snapshotJson, Map.class);
    }

    private ZoneId zoneId() {
        try {
            return ZoneId.of(props.zone());
        } catch (Exception e) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private static String shortId(UUID userId) {
        String raw = userId.toString();
        return raw.length() <= 8 ? raw : raw.substring(0, 8);
    }

    private static String truncate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    /** 한 번의 일일 스윕 결과 요약. */
    public record SweepResult(int activeUsers, int triggered, int skipped, int failed) {
    }
}
