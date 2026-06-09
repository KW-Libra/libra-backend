package com.libra.api.scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.libra.api.agent.api.dto.RunStartRequest;
import com.libra.api.agent.service.AgentSseClient;
import com.libra.api.auth.domain.User;
import com.libra.api.auth.domain.UserRepository;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import com.libra.api.ingest.service.LiveIngestService;
import com.libra.api.portfolio.domain.PortfolioSnapshot;
import com.libra.api.portfolio.domain.PortfolioSnapshotRepository;
import com.libra.api.scheduler.config.SchedulerProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DailyRebalanceSchedulerTests {

    private final UserRepository users = mock(UserRepository.class);
    private final PortfolioSnapshotRepository snapshots = mock(PortfolioSnapshotRepository.class);
    private final LiveIngestService ingest = mock(LiveIngestService.class);
    private final AgentSseClient agent = mock(AgentSseClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SchedulerProperties props =
        new SchedulerProperties(true, null, null, null, null, null, null);

    private final DailyRebalanceScheduler scheduler =
        new DailyRebalanceScheduler(props, users, snapshots, ingest, agent, objectMapper);

    // Mockito 주의: 아래 헬퍼는 내부에서 stubbing 하므로 when(...).thenReturn(...) 인자 안에서
    // 호출하면 UnfinishedStubbingException 이 난다. 반드시 stubbing 밖에서 변수로 만들어 사용한다.
    private User userWithId(UUID id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private PortfolioSnapshot snapshotWith(String json) {
        PortfolioSnapshot snapshot = mock(PortfolioSnapshot.class);
        when(snapshot.getSnapshotJson()).thenReturn(json);
        return snapshot;
    }

    @Test
    void triggersOneRunPerActiveUserWithSnapshot() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User u1 = userWithId(id1);
        User u2 = userWithId(id2);
        PortfolioSnapshot s1 = snapshotWith("{\"holdings\":[{\"symbol\":\"005930\"}]}");
        PortfolioSnapshot s2 = snapshotWith("{\"holdings\":[{\"symbol\":\"000660\"}]}");

        when(users.findByActiveTrue()).thenReturn(List.of(u1, u2));
        when(snapshots.findFirstByUserIdOrderByCreatedAtDesc(id1)).thenReturn(Optional.of(s1));
        when(snapshots.findFirstByUserIdOrderByCreatedAtDesc(id2)).thenReturn(Optional.of(s2));
        when(ingest.prepare(any(RunStartRequest.class), any(User.class), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(agent.runToCompletion(any(), any(User.class), any()))
            .thenReturn(new AgentSseClient.ScheduledRunOutcome(3, "decision", "{}"));

        DailyRebalanceScheduler.SweepResult result = scheduler.runForAllActiveUsers();

        assertThat(result.activeUsers()).isEqualTo(2);
        assertThat(result.triggered()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        verify(agent, times(2)).runToCompletion(any(), any(User.class), any());
    }

    @Test
    void isolatesPerUserFailures() {
        UUID failing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        User uFail = userWithId(failing);
        User uOk = userWithId(ok);
        PortfolioSnapshot snap = snapshotWith("{\"holdings\":[{\"symbol\":\"005930\"}]}");

        when(users.findByActiveTrue()).thenReturn(List.of(uFail, uOk));
        when(snapshots.findFirstByUserIdOrderByCreatedAtDesc(any())).thenReturn(Optional.of(snap));
        when(ingest.prepare(any(RunStartRequest.class), eq(uFail), any()))
            .thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED));
        when(ingest.prepare(any(RunStartRequest.class), eq(uOk), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(agent.runToCompletion(any(), eq(uOk), any()))
            .thenReturn(new AgentSseClient.ScheduledRunOutcome(1, "decision", "{}"));

        DailyRebalanceScheduler.SweepResult result = scheduler.runForAllActiveUsers();

        assertThat(result.triggered()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(agent, times(1)).runToCompletion(any(), any(User.class), any());
    }

    @Test
    void skipsUsersWithoutSnapshot() {
        UUID id = UUID.randomUUID();
        User user = userWithId(id);

        when(users.findByActiveTrue()).thenReturn(List.of(user));
        when(snapshots.findFirstByUserIdOrderByCreatedAtDesc(id)).thenReturn(Optional.empty());

        DailyRebalanceScheduler.SweepResult result = scheduler.runForAllActiveUsers();

        assertThat(result.triggered()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(agent, never()).runToCompletion(any(), any(User.class), any());
    }
}
