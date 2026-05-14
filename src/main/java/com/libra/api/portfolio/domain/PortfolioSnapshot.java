package com.libra.api.portfolio.domain;

import com.libra.api.broker.kis.api.dto.KisBalanceResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "portfolio_snapshots")
@EntityListeners(AuditingEntityListener.class)
public class PortfolioSnapshot {

    public static final String PROVIDER_KIS = "KIS";
    public static final String SOURCE_KIS_BALANCE = "kis_balance";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String provider;

    @Column(nullable = false, length = 16)
    private String environment;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "holdings_count", nullable = false)
    private int holdingsCount;

    @Column(name = "deposit_amount", precision = 20, scale = 4)
    private BigDecimal depositAmount;

    @Column(name = "stock_valuation_amount", precision = 20, scale = 4)
    private BigDecimal stockValuationAmount;

    @Column(name = "total_valuation_amount", precision = 20, scale = 4)
    private BigDecimal totalValuationAmount;

    @Column(name = "net_asset_amount", precision = 20, scale = 4)
    private BigDecimal netAssetAmount;

    @Column(name = "purchase_amount", precision = 20, scale = 4)
    private BigDecimal purchaseAmount;

    @Column(name = "valuation_amount", precision = 20, scale = 4)
    private BigDecimal valuationAmount;

    @Column(name = "profit_loss_amount", precision = 20, scale = 4)
    private BigDecimal profitLossAmount;

    @Column(name = "profit_loss_rate", precision = 20, scale = 4)
    private BigDecimal profitLossRate;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "trace_id", length = 80)
    private String traceId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PortfolioSnapshot() {
        // JPA
    }

    public static PortfolioSnapshot fromKisBalance(
        UUID userId,
        KisBalanceResponse balance,
        String snapshotJson,
        String traceId
    ) {
        KisBalanceResponse.AccountSummary summary = balance.summary();
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.userId = userId;
        snapshot.provider = PROVIDER_KIS;
        snapshot.environment = balance.environment();
        snapshot.source = SOURCE_KIS_BALANCE;
        snapshot.holdingsCount = balance.holdings().size();
        snapshot.depositAmount = summary.depositAmount();
        snapshot.stockValuationAmount = summary.stockValuationAmount();
        snapshot.totalValuationAmount = summary.totalValuationAmount();
        snapshot.netAssetAmount = summary.netAssetAmount();
        snapshot.purchaseAmount = summary.purchaseAmount();
        snapshot.valuationAmount = summary.valuationAmount();
        snapshot.profitLossAmount = summary.profitLossAmount();
        snapshot.profitLossRate = summary.profitLossRate();
        snapshot.snapshotJson = snapshotJson;
        snapshot.traceId = traceId;
        return snapshot;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getEnvironment() { return environment; }
    public String getSource() { return source; }
    public int getHoldingsCount() { return holdingsCount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public BigDecimal getStockValuationAmount() { return stockValuationAmount; }
    public BigDecimal getTotalValuationAmount() { return totalValuationAmount; }
    public BigDecimal getNetAssetAmount() { return netAssetAmount; }
    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public BigDecimal getValuationAmount() { return valuationAmount; }
    public BigDecimal getProfitLossAmount() { return profitLossAmount; }
    public BigDecimal getProfitLossRate() { return profitLossRate; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
}
