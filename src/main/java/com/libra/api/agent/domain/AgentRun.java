package com.libra.api.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One persisted agent deliberation run (a "session" in the History tab).
 */
@Entity
@Table(name = "agent_runs")
@EntityListeners(AuditingEntityListener.class)
public class AgentRun {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "thread_id", length = 80)
    private String threadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRunStatus status = AgentRunStatus.RUNNING;

    @Column(columnDefinition = "text")
    private String query;

    @Column(length = 20)
    private String trigger;

    @Column(name = "final_decision", length = 40)
    private String finalDecision;

    @Column(name = "final_branch", length = 60)
    private String finalBranch;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "event_count", nullable = false)
    private int eventCount = 0;

    @Column(name = "trace_id", length = 80)
    private String traceId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentRun() {
        // JPA
    }

    public static AgentRun start(UUID userId, String traceId, String query, String trigger) {
        AgentRun run = new AgentRun();
        run.userId = userId;
        run.traceId = traceId;
        run.query = query;
        run.trigger = trigger;
        run.status = AgentRunStatus.RUNNING;
        return run;
    }

    public void attachThread(String threadId) {
        if (threadId != null && !threadId.isBlank()) {
            this.threadId = threadId;
        }
    }

    public void markCompleted(String decision, String branch, String summary, int eventCount) {
        this.status = AgentRunStatus.COMPLETED;
        this.finalDecision = truncate(decision, 40);
        this.finalBranch = truncate(branch, 60);
        this.summary = summary;
        this.eventCount = eventCount;
        this.completedAt = Instant.now();
    }

    public void markFailed(int eventCount) {
        this.status = AgentRunStatus.FAILED;
        this.eventCount = eventCount;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getThreadId() { return threadId; }
    public AgentRunStatus getStatus() { return status; }
    public String getQuery() { return query; }
    public String getTrigger() { return trigger; }
    public String getFinalDecision() { return finalDecision; }
    public String getFinalBranch() { return finalBranch; }
    public String getSummary() { return summary; }
    public int getEventCount() { return eventCount; }
    public String getTraceId() { return traceId; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
