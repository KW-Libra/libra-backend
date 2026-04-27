package com.libra.api.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rebalance_plan_items")
public class RebalancePlanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_run_id", nullable = false, length = 36)
    private String decisionRunId;

    @Column(name = "ticker", nullable = false, length = 32)
    private String ticker;

    @Column(name = "weight_delta", nullable = false, precision = 8, scale = 6)
    private BigDecimal weightDelta;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RebalancePlanItemEntity() {
    }

    public RebalancePlanItemEntity(
            String decisionRunId,
            String ticker,
            BigDecimal weightDelta,
            LocalDateTime createdAt
    ) {
        this.decisionRunId = decisionRunId;
        this.ticker = ticker;
        this.weightDelta = weightDelta;
        this.createdAt = createdAt;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getWeightDelta() {
        return weightDelta;
    }
}
