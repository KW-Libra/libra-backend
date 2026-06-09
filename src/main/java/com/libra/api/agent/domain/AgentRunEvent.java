package com.libra.api.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One SSE event captured during a deliberation run, in stream order.
 */
@Entity
@Table(name = "agent_run_events")
public class AgentRunEvent {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "run_id", nullable = false, columnDefinition = "uuid")
    private UUID runId;

    @Column(name = "event_index", nullable = false)
    private int eventIndex;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "event_data", columnDefinition = "text")
    private String eventData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentRunEvent() {
        // JPA
    }

    public static AgentRunEvent of(UUID runId, int eventIndex, String eventType, String eventData) {
        AgentRunEvent event = new AgentRunEvent();
        event.runId = runId;
        event.eventIndex = eventIndex;
        event.eventType = truncate(eventType, 40);
        event.eventData = eventData;
        event.createdAt = Instant.now();
        return event;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public int getEventIndex() { return eventIndex; }
    public String getEventType() { return eventType; }
    public String getEventData() { return eventData; }
    public Instant getCreatedAt() { return createdAt; }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
