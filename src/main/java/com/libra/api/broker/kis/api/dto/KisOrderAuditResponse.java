package com.libra.api.broker.kis.api.dto;

import com.libra.api.broker.kis.domain.KisOrderAudit;
import com.libra.api.broker.kis.domain.KisOrderAuditStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KisOrderAuditResponse(
    UUID id,
    UUID userId,
    String provider,
    String environment,
    KisOrderAuditStatus status,
    KisOrderSide side,
    String symbol,
    long quantity,
    BigDecimal price,
    String orderDivision,
    String exchangeId,
    boolean dryRun,
    boolean tradingEnabled,
    String brokerOrderNo,
    String brokerMessage,
    String errorCode,
    String errorMessage,
    String traceId,
    Instant createdAt,
    Instant updatedAt
) {

    public static KisOrderAuditResponse from(KisOrderAudit audit) {
        return new KisOrderAuditResponse(
            audit.getId(),
            audit.getUserId(),
            audit.getProvider(),
            audit.getEnvironment(),
            audit.getStatus(),
            audit.getSide(),
            audit.getSymbol(),
            audit.getQuantity(),
            audit.getPrice(),
            audit.getOrderDivision(),
            audit.getExchangeId(),
            audit.isDryRun(),
            audit.isTradingEnabled(),
            audit.getBrokerOrderNo(),
            audit.getBrokerMessage(),
            audit.getErrorCode(),
            audit.getErrorMessage(),
            audit.getTraceId(),
            audit.getCreatedAt(),
            audit.getUpdatedAt()
        );
    }
}
