package com.libra.api.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_signals")
public class AgentSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_run_id", nullable = false, length = 36)
    private String decisionRunId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "opinion_id", nullable = false, length = 128)
    private String opinionId;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Column(name = "verdict", nullable = false, length = 64)
    private String verdict;

    @Column(name = "direction", nullable = false, precision = 7, scale = 5)
    private BigDecimal direction;

    @Column(name = "strength", nullable = false, precision = 6, scale = 5)
    private BigDecimal strength;

    @Column(name = "urgency", nullable = false, length = 32)
    private String urgency;

    @Column(name = "confidence", nullable = false, precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "signal_score", precision = 7, scale = 5)
    private BigDecimal signalScore;

    @Column(name = "source_trust", precision = 6, scale = 5)
    private BigDecimal sourceTrust;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "horizon", length = 64)
    private String horizon;

    @Lob
    @Column(name = "focus_tickers", nullable = false)
    private String focusTickers;

    @Lob
    @Column(name = "evidence", nullable = false)
    private String evidence;

    @Lob
    @Column(name = "tools_called", nullable = false)
    private String toolsCalled;

    @Lob
    @Column(name = "reasoning", nullable = false)
    private String reasoning;

    @Lob
    @Column(name = "limits_acknowledged")
    private String limitsAcknowledged;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AgentSignalEntity() {
    }

    public AgentSignalEntity(
            String decisionRunId,
            String agentId,
            String opinionId,
            int turnNumber,
            String verdict,
            BigDecimal direction,
            BigDecimal strength,
            String urgency,
            BigDecimal confidence,
            BigDecimal signalScore,
            BigDecimal sourceTrust,
            String eventType,
            String horizon,
            String focusTickers,
            String evidence,
            String toolsCalled,
            String reasoning,
            String limitsAcknowledged,
            LocalDateTime createdAt
    ) {
        this.decisionRunId = decisionRunId;
        this.agentId = agentId;
        this.opinionId = opinionId;
        this.turnNumber = turnNumber;
        this.verdict = verdict;
        this.direction = direction;
        this.strength = strength;
        this.urgency = urgency;
        this.confidence = confidence;
        this.signalScore = signalScore;
        this.sourceTrust = sourceTrust;
        this.eventType = eventType;
        this.horizon = horizon;
        this.focusTickers = focusTickers;
        this.evidence = evidence;
        this.toolsCalled = toolsCalled;
        this.reasoning = reasoning;
        this.limitsAcknowledged = limitsAcknowledged;
        this.createdAt = createdAt;
    }

    public String getAgentId() {
        return agentId;
    }

    public BigDecimal getSignalScore() {
        return signalScore;
    }
}
