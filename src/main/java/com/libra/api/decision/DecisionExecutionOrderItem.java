package com.libra.api.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record DecisionExecutionOrderItem(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "ticker must be a 6 digit KRX code")
        String ticker,
        @NotBlank
        @Pattern(regexp = "BUY|SELL", flags = Pattern.Flag.CASE_INSENSITIVE, message = "side must be BUY or SELL")
        String side,
        @Positive
        long quantity,
        @PositiveOrZero
        BigDecimal priceKrw,
        @Pattern(regexp = "00|01", message = "order_type must be 00(limit) or 01(market)")
        String orderType
) {
}
