package com.libra.api.backtest.api.dto;

public record BacktestTradeAlphaSummaryResponse(
    int signals,
    int v3Executed,
    int v3Skipped,
    int v1Negative20d,
    int v1Negative60d,
    int v3Negative20d,
    int v3Negative60d
) {
}
