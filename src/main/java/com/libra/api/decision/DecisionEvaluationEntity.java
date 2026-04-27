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
@Table(name = "evaluations")
public class DecisionEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_run_id", nullable = false, length = 36)
    private String decisionRunId;

    @Column(name = "horizon", nullable = false, length = 16)
    private String horizon;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @Column(name = "direction_accuracy")
    private Boolean directionAccuracy;

    @Column(name = "timing_accuracy", precision = 6, scale = 5)
    private BigDecimal timingAccuracy;

    @Column(name = "magnitude_error", precision = 12, scale = 6)
    private BigDecimal magnitudeError;

    @Column(name = "cost_efficiency", precision = 12, scale = 6)
    private BigDecimal costEfficiency;

    @Column(name = "fast_track_accuracy")
    private Boolean fastTrackAccuracy;

    @Column(name = "verdict", nullable = false, length = 64)
    private String verdict;

    @Lob
    @Column(name = "notes")
    private String notes;

    @Lob
    @Column(name = "metrics_payload", nullable = false)
    private String metricsPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DecisionEvaluationEntity() {
    }

    public DecisionEvaluationEntity(
            String decisionRunId,
            String horizon,
            LocalDateTime evaluatedAt,
            Boolean directionAccuracy,
            BigDecimal timingAccuracy,
            BigDecimal magnitudeError,
            BigDecimal costEfficiency,
            Boolean fastTrackAccuracy,
            String verdict,
            String notes,
            String metricsPayload,
            LocalDateTime createdAt
    ) {
        this.decisionRunId = decisionRunId;
        this.horizon = horizon;
        this.evaluatedAt = evaluatedAt;
        this.directionAccuracy = directionAccuracy;
        this.timingAccuracy = timingAccuracy;
        this.magnitudeError = magnitudeError;
        this.costEfficiency = costEfficiency;
        this.fastTrackAccuracy = fastTrackAccuracy;
        this.verdict = verdict;
        this.notes = notes;
        this.metricsPayload = metricsPayload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getDecisionRunId() {
        return decisionRunId;
    }

    public String getHorizon() {
        return horizon;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public Boolean getDirectionAccuracy() {
        return directionAccuracy;
    }

    public BigDecimal getTimingAccuracy() {
        return timingAccuracy;
    }

    public BigDecimal getMagnitudeError() {
        return magnitudeError;
    }

    public BigDecimal getCostEfficiency() {
        return costEfficiency;
    }

    public Boolean getFastTrackAccuracy() {
        return fastTrackAccuracy;
    }

    public String getVerdict() {
        return verdict;
    }

    public String getNotes() {
        return notes;
    }

    public String getMetricsPayload() {
        return metricsPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
