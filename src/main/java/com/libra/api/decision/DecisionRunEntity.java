package com.libra.api.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "decision_runs")
public class DecisionRunEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "thread_id", nullable = false, length = 128)
    private String threadId;

    @Lob
    @Column(name = "query", nullable = false)
    private String query;

    @Column(name = "model", nullable = false, length = 128)
    private String model;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "decision", nullable = false, length = 64)
    private String decision;

    @Column(name = "urgency", nullable = false, length = 32)
    private String urgency;

    @Column(name = "confidence", nullable = false, precision = 6, scale = 5)
    private BigDecimal confidence;

    @Column(name = "consensus_score", nullable = false, precision = 7, scale = 5)
    private BigDecimal consensusScore;

    @Column(name = "divergence_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal divergenceScore;

    @Column(name = "needs_trade_evaluation", nullable = false)
    private boolean needsTradeEvaluation;

    @Column(name = "follow_up_at")
    private LocalDateTime followUpAt;

    @Column(name = "feedback_checkpoint_at")
    private LocalDateTime feedbackCheckpointAt;

    @Lob
    @Column(name = "portfolio_snapshot", nullable = false)
    private String portfolioSnapshot;

    @Lob
    @Column(name = "request_payload", nullable = false)
    private String requestPayload;

    @Lob
    @Column(name = "result_payload", nullable = false)
    private String resultPayload;

    @Lob
    @Column(name = "knowledge_sources", nullable = false)
    private String knowledgeSources;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DecisionRunEntity() {
    }

    public DecisionRunEntity(
            String id,
            String userId,
            String threadId,
            String query,
            String model,
            String triggerType,
            String decision,
            String urgency,
            BigDecimal confidence,
            BigDecimal consensusScore,
            BigDecimal divergenceScore,
            boolean needsTradeEvaluation,
            LocalDateTime followUpAt,
            LocalDateTime feedbackCheckpointAt,
            String portfolioSnapshot,
            String requestPayload,
            String resultPayload,
            String knowledgeSources,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.threadId = threadId;
        this.query = query;
        this.model = model;
        this.triggerType = triggerType;
        this.decision = decision;
        this.urgency = urgency;
        this.confidence = confidence;
        this.consensusScore = consensusScore;
        this.divergenceScore = divergenceScore;
        this.needsTradeEvaluation = needsTradeEvaluation;
        this.followUpAt = followUpAt;
        this.feedbackCheckpointAt = feedbackCheckpointAt;
        this.portfolioSnapshot = portfolioSnapshot;
        this.requestPayload = requestPayload;
        this.resultPayload = resultPayload;
        this.knowledgeSources = knowledgeSources;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getQuery() {
        return query;
    }

    public String getModel() {
        return model;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getDecision() {
        return decision;
    }

    public String getUrgency() {
        return urgency;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public BigDecimal getConsensusScore() {
        return consensusScore;
    }

    public BigDecimal getDivergenceScore() {
        return divergenceScore;
    }

    public boolean isNeedsTradeEvaluation() {
        return needsTradeEvaluation;
    }

    public LocalDateTime getFollowUpAt() {
        return followUpAt;
    }

    public LocalDateTime getFeedbackCheckpointAt() {
        return feedbackCheckpointAt;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
