package com.libra.api.backtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.backtest.api.dto.BacktestValidationResponse;
import com.libra.api.backtest.config.BacktestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BacktestValidationServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void readsValidationArtifactsWithoutMockData() throws Exception {
        Path experimentDir = tempDir.resolve(BacktestValidationService.FINAL_JUDGE_EXPERIMENT_ID);
        Files.createDirectories(experimentDir);
        Files.writeString(
            experimentDir.resolve("confirmation-v3-results.article-supergemma-v2-finaljudge-full.json"),
            """
            {
              "ranked_by_ending_value": [
                {
                  "strategy": "LIBRA-v3 T+2 Confirmation Gate",
                  "group": "libra_v3_confirmation_gate",
                  "ending_value_krw": 151616342.27,
                  "total_return_pct": 51.616,
                  "annualized_volatility_pct": 23.887,
                  "sharpe_ratio": 0.565,
                  "max_drawdown_pct": -38.937,
                  "trades": 5,
                  "turnover_krw": 114730113.61,
                  "transaction_cost_krw": 344190.34,
                  "return_gap_vs_libra_pct_points": 1.668
                },
                {
                  "strategy": "LIBRA",
                  "ending_value_krw": 149948258.39,
                  "total_return_pct": 49.948,
                  "annualized_volatility_pct": 23.84,
                  "sharpe_ratio": 0.553,
                  "max_drawdown_pct": -38.937,
                  "trades": 8,
                  "turnover_krw": 132575228.66,
                  "transaction_cost_krw": 397725.69,
                  "return_gap_vs_libra_pct_points": 0.0
                }
              ]
            }
            """
        );
        Files.writeString(
            experimentDir.resolve("libra-v1-v3-trade-alpha-comparison.csv"),
            """
            signal_date,v3_policy,v1_execute_date,v3_confirmation_date,v3_execute_date,v3_was_skipped,v1_cost_krw,v3_cost_krw,v1_trade_alpha_20d_pct,v3_trade_alpha_20d_pct,improvement_20d_pct,v1_trade_alpha_60d_pct,v3_trade_alpha_60d_pct,improvement_60d_pct,improvement
            2020-04-23,T+2 Confirmation Gate,2020-04-23,2020-04-27,2020-04-28,false,36091.53,36385.35,-1.078,-0.602,0.476,-1.054,-1.622,-0.568,-0.568
            2020-06-23,T+2 Confirmation Gate,2020-06-23,2020-06-25,,true,29218.32,0.0,0.706,0.0,-0.706,1.235,0.0,-1.235,-1.235
            """
        );

        BacktestValidationService service = new BacktestValidationService(
            new BacktestProperties(tempDir)
        );

        BacktestValidationResponse response = service.getValidation(BacktestValidationService.FINAL_JUDGE_EXPERIMENT_ID);

        assertThat(response.mainCandidate().strategy()).isEqualTo("LIBRA-v3 T+2 Confirmation Gate");
        assertThat(response.libraV1().strategy()).isEqualTo("LIBRA");
        assertThat(response.tradeAlphaSummary().signals()).isEqualTo(2);
        assertThat(response.tradeAlphaSummary().v3Executed()).isEqualTo(1);
        assertThat(response.tradeAlphaSummary().v3Skipped()).isEqualTo(1);
        assertThat(response.tradeAlphaSummary().v1Negative20d()).isEqualTo(1);
        assertThat(response.tradeAlpha()).hasSize(2);
    }
}
