package com.libra.api.integration.kis;

import java.math.BigDecimal;
import java.util.Map;

public record KisCashOrderResult(
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal priceKrw,
        BigDecimal amountKrw,
        String status,
        String message,
        String orderNo,
        String orderTime,
        Map<String, Object> rawPayload
) {
}
