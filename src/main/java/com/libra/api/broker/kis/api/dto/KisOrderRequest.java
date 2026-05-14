package com.libra.api.broker.kis.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record KisOrderRequest(
    @NotNull KisOrderSide side,
    @Pattern(regexp = "^[A-Z0-9]{5,12}$", message = "symbol must be 5-12 uppercase alphanumeric characters")
    String symbol,
    @Min(1) long quantity,
    BigDecimal price,
    String orderDivision,
    String exchangeId
) {

    public KisOrderRequest {
        if (orderDivision == null || orderDivision.isBlank()) {
            orderDivision = price == null || BigDecimal.ZERO.compareTo(price) == 0 ? "01" : "00";
        }
        if (exchangeId == null || exchangeId.isBlank()) {
            exchangeId = "KRX";
        }
        if (price == null) {
            price = BigDecimal.ZERO;
        }
    }
}
