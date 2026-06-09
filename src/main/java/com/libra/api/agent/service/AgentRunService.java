package com.libra.api.agent.service;

import com.libra.api.agent.domain.AgentRun;
import com.libra.api.agent.domain.AgentRunEvent;
import com.libra.api.agent.domain.AgentRunEventRepository;
import com.libra.api.agent.domain.AgentRunRepository;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists agent deliberation runs and their event streams so the History tab
 * shows real past sessions instead of in-memory/demo data.
 */
@Service
public class AgentRunService {

    private final AgentRunRepository runs;
    private final AgentRunEventRepository events;

    public AgentRunService(AgentRunRepository runs, AgentRunEventRepository events) {
        this.runs = runs;
        this.events = events;
    }

    @Transactional
    public UUID createRun(UUID userId, String traceId, String query, String trigger) {
        return runs.save(AgentRun.start(userId, traceId, query, trigger)).getId();
    }

    @Transactional
    public void recordEvent(UUID runId, int eventIndex, String eventType, String eventData) {
        events.save(AgentRunEvent.of(runId, eventIndex, eventType, eventData));
    }

    @Transactional
    public void attachThread(UUID runId, String threadId) {
        runs.findById(runId).ifPresent(run -> run.attachThread(threadId));
    }

    @Transactional
    public void completeRun(UUID runId, String decision, String branch, String summary, int eventCount) {
        runs.findById(runId).ifPresent(run -> run.markCompleted(decision, branch, summary, eventCount));
    }

    @Transactional
    public void failRun(UUID runId, int eventCount) {
        runs.findById(runId).ifPresent(run -> run.markFailed(eventCount));
    }

    @Transactional(readOnly = true)
    public Page<AgentRun> listRuns(UUID userId, Pageable pageable) {
        return runs.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public UUID findRunIdByThread(UUID userId, String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return null;
        }
        return runs.findFirstByThreadIdAndUserIdOrderByCreatedAtDesc(threadId, userId)
            .map(AgentRun::getId)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public AgentRun getRun(UUID userId, UUID runId) {
        return runs.findByIdAndUserId(runId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "심의 세션을 찾을 수 없습니다"));
    }

    @Transactional(readOnly = true)
    public List<AgentRunEvent> transcript(UUID userId, UUID runId) {
        getRun(userId, runId); // ownership check
        return events.findByRunIdOrderByEventIndex(runId);
    }
}
