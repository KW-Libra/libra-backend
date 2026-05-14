package com.libra.api.broker.kis.api.dto;

import java.math.BigDecimal;
import java.util.Map;

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
    Map<String, Object> raw
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
            Map.of()
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
            Map.copyOf(raw)
        );
    }
}
