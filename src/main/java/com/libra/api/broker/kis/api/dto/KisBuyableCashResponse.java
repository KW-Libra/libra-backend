package com.libra.api.broker.kis.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record KisBuyableCashResponse(
    String environment,
    String symbol,
    BigDecimal price,
    String orderDivision,
    BigDecimal buyableAmountWithoutMargin,
    BigDecimal buyableQuantityWithoutMargin,
    BigDecimal maxBuyableAmount,
    BigDecimal maxBuyableQuantity,
    Map<String, Object> raw
) {

    public static KisBuyableCashResponse from(
        String environment,
        String symbol,
        BigDecimal price,
        String orderDivision,
        Map<String, Object> output
    ) {
        return new KisBuyableCashResponse(
            environment,
            symbol,
            price,
            orderDivision,
            decimalValue(output, "nrcvb_buy_amt"),
            decimalValue(output, "nrcvb_buy_qty"),
            decimalValue(output, "max_buy_amt"),
            decimalValue(output, "max_buy_qty"),
            Map.copyOf(output)
        );
    }

    private static BigDecimal decimalValue(Map<String, Object> output, String key) {
        Object raw = output.get(key);
        if (raw == null) {
            return null;
        }
        String value = raw.toString().replace(",", "").trim();
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }
}
