package com.libra.api.broker.kis.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record KisQuoteResponse(
    String symbol,
    String marketCode,
    String name,
    BigDecimal price,
    BigDecimal change,
    BigDecimal changeRate,
    Long accumulatedVolume,
    Map<String, Object> raw
) {

    public static KisQuoteResponse from(String symbol, String marketCode, Map<String, Object> output) {
        return new KisQuoteResponse(
            symbol,
            marketCode,
            stringValue(output, "hts_kor_isnm"),
            decimalValue(output, "stck_prpr"),
            decimalValue(output, "prdy_vrss"),
            decimalValue(output, "prdy_ctrt"),
            longValue(output, "acml_vol"),
            Map.copyOf(output)
        );
    }

    private static String stringValue(Map<String, Object> output, String key) {
        Object value = output.get(key);
        return value == null ? "" : value.toString();
    }

    private static BigDecimal decimalValue(Map<String, Object> output, String key) {
        String value = stringValue(output, key).replace(",", "").trim();
        if (value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static Long longValue(Map<String, Object> output, String key) {
        String value = stringValue(output, key).replace(",", "").trim();
        if (value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }
}
