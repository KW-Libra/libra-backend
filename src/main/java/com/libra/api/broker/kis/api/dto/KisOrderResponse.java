package com.libra.api.broker.kis.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record KisOrderResponse(
    boolean dryRun,
    boolean submitted,
    String environment,
    KisOrderSide side,
    String symbol,
    long quantity,
    BigDecimal price,
    String orderDivision,
    String exchangeId,
    String message,
    Map<String, Object> raw,
    UUID auditId
) {

    public static KisOrderResponse dryRun(String environment, KisOrderRequest request) {
        return new KisOrderResponse(
            true,
            false,
            environment,
            request.side(),
            request.symbol(),
            request.quantity(),
            request.price(),
            request.orderDivision(),
            request.exchangeId(),
            "dry_run_only",
            Map.of(),
            null
        );
    }

    public static KisOrderResponse submitted(
        String environment,
        KisOrderRequest request,
        String message,
        Map<String, Object> raw
    ) {
        return new KisOrderResponse(
            false,
            true,
            environment,
            request.side(),
            request.symbol(),
            request.quantity(),
            request.price(),
            request.orderDivision(),
            request.exchangeId(),
            message,
            Map.copyOf(raw),
            null
        );
    }

    public KisOrderResponse withAuditId(UUID auditId) {
        return new KisOrderResponse(
            dryRun,
            submitted,
            environment,
            side,
            symbol,
            quantity,
            price,
            orderDivision,
            exchangeId,
            message,
            raw,
            auditId
        );
    }
}
