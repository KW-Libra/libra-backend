package com.libra.api.backtest.api.dto;

import java.math.BigDecimal;

public record BacktestMetricResponse(
    String strategy,
    String group,
    BigDecimal endingValueKrw,
    BigDecimal totalReturnPct,
    BigDecimal annualizedVolatilityPct,
    BigDecimal sharpeRatio,
    BigDecimal maxDrawdownPct,
    Integer trades,
    BigDecimal turnoverKrw,
    BigDecimal transactionCostKrw,
    BigDecimal returnGapVsLibraPctPoints
) {
}
