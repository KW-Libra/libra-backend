package com.libra.api.backtest.api.dto;

import java.util.List;

public record BacktestValidationResponse(
    String experimentId,
    String title,
    String period,
    String dataSource,
    String framing,
    BacktestMetricResponse mainCandidate,
    BacktestMetricResponse libraV1,
    List<BacktestMetricResponse> results,
    BacktestTradeAlphaSummaryResponse tradeAlphaSummary,
    List<BacktestTradeAlphaResponse> tradeAlpha,
    List<String> artifacts,
    List<String> notes
) {
}
