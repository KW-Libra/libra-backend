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
@Table(name = "executions")
public class DecisionExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_run_id", nullable = false, length = 36)
    private String decisionRunId;

    @Column(name = "ticker", nullable = false, length = 32)
    private String ticker;

    @Column(name = "side", nullable = false, length = 8)
    private String side;

    @Column(name = "quantity", precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(name = "price_krw", precision = 20, scale = 4)
    private BigDecimal priceKrw;

    @Column(name = "amount_krw", precision = 20, scale = 4)
    private BigDecimal amountKrw;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Lob
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DecisionExecutionEntity() {
    }

    public DecisionExecutionEntity(
            String decisionRunId,
            String ticker,
            String side,
            BigDecimal quantity,
            BigDecimal priceKrw,
            BigDecimal amountKrw,
            LocalDateTime executedAt,
            String status,
            String rawPayload,
            LocalDateTime createdAt
    ) {
        this.decisionRunId = decisionRunId;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
        this.priceKrw = priceKrw;
        this.amountKrw = amountKrw;
        this.executedAt = executedAt;
        this.status = status;
        this.rawPayload = rawPayload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getDecisionRunId() {
        return decisionRunId;
    }

    public String getTicker() {
        return ticker;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceKrw() {
        return priceKrw;
    }

    public BigDecimal getAmountKrw() {
        return amountKrw;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
