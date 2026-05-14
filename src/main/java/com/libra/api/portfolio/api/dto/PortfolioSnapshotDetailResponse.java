package com.libra.api.portfolio.api.dto;

import com.libra.api.portfolio.domain.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioSnapshotDetailResponse(
    UUID id,
    UUID userId,
    String provider,
    String environment,
    String source,
    int holdingsCount,
    BigDecimal depositAmount,
    BigDecimal stockValuationAmount,
    BigDecimal totalValuationAmount,
    BigDecimal netAssetAmount,
    BigDecimal purchaseAmount,
    BigDecimal valuationAmount,
    BigDecimal profitLossAmount,
    BigDecimal profitLossRate,
    String snapshotJson,
    String traceId,
    Instant createdAt
) {

    public static PortfolioSnapshotDetailResponse from(PortfolioSnapshot snapshot) {
        return new PortfolioSnapshotDetailResponse(
            snapshot.getId(),
            snapshot.getUserId(),
            snapshot.getProvider(),
            snapshot.getEnvironment(),
            snapshot.getSource(),
            snapshot.getHoldingsCount(),
            snapshot.getDepositAmount(),
            snapshot.getStockValuationAmount(),
            snapshot.getTotalValuationAmount(),
            snapshot.getNetAssetAmount(),
            snapshot.getPurchaseAmount(),
            snapshot.getValuationAmount(),
            snapshot.getProfitLossAmount(),
            snapshot.getProfitLossRate(),
            snapshot.getSnapshotJson(),
            snapshot.getTraceId(),
            snapshot.getCreatedAt()
        );
    }
}
