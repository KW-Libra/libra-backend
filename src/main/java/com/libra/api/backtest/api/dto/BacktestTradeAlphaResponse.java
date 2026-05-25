package com.libra.api.backtest.api.dto;

import java.math.BigDecimal;

public record BacktestTradeAlphaResponse(
    String signalDate,
    String v3Policy,
    String v1ExecuteDate,
    String v3ConfirmationDate,
    String v3ExecuteDate,
    boolean v3WasSkipped,
    BigDecimal v1TradeAlpha20dPct,
    BigDecimal v3TradeAlpha20dPct,
    BigDecimal improvement20dPct,
    BigDecimal v1TradeAlpha60dPct,
    BigDecimal v3TradeAlpha60dPct,
    BigDecimal improvement60dPct
) {
}
