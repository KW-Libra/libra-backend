package com.libra.api.integration.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.libra.api.portfolio.PortfolioHolding;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class KisPortfolioMapperTest {

    private final KisPortfolioMapper mapper = new KisPortfolioMapper();

    @Test
    void mapsHoldingsAndCashWeightIntoPortfolioSnapshot() {
        PortfolioSnapshot snapshot = mapper.toSnapshot(
                List.of(
                        new KisBalanceHoldingRow("005930", "삼성전자", "10", "650000", "65000", "60000", "50000"),
                        new KisBalanceHoldingRow("000660", "SK하이닉스", "5", "500000", "100000", "90000", "-50000")
                ),
                List.of(new KisBalanceSummaryRow("350000", "1500000", "1150000")),
                List.of("국내 대형주 중심")
        );

        assertThat(snapshot.totalValueKrw()).isEqualTo(1500000d);
        assertThat(snapshot.cashWeight()).isEqualTo(350000d / 1500000d);
        assertThat(snapshot.userPreferences()).containsExactly("국내 대형주 중심");
        assertThat(snapshot.holdings()).hasSize(2);

        PortfolioHolding first = snapshot.holdings().get(0);
        assertThat(first.ticker()).isEqualTo("005930");
        assertThat(first.companyName()).isEqualTo("삼성전자");
        assertThat(first.weight()).isCloseTo(650000d / 1500000d, within(0.000001d));
        assertThat(first.aliases()).contains("005930.KS", "KRX:005930");
        assertThat(first.shares()).isEqualTo(10d);
        assertThat(first.lastPrice()).isEqualTo(65000d);
        assertThat(first.averagePrice()).isEqualTo(60000d);
        assertThat(first.marketValueKrw()).isEqualTo(650000d);
        assertThat(first.unrealizedPnlKrw()).isEqualTo(50000d);
    }

    @Test
    void skipsRowsWithoutPosition() {
        PortfolioSnapshot snapshot = mapper.toSnapshot(
                List.of(
                        new KisBalanceHoldingRow("005930", "삼성전자", "0", "0", "65000"),
                        new KisBalanceHoldingRow("000660", "SK 하이닉스", "3", "300000", "100000")
                ),
                List.of(new KisBalanceSummaryRow("700000", "1000000", "300000")),
                null
        );

        assertThat(snapshot.holdings()).hasSize(1);
        assertThat(snapshot.holdings().get(0).aliases()).contains("SK하이닉스");
        assertThat(snapshot.userPreferences()).isEmpty();
    }

    @Test
    void fallsBackToQuantityTimesLastPriceWhenKisEvaluationAmountIsMissing() {
        PortfolioSnapshot snapshot = mapper.toSnapshot(
                List.of(new KisBalanceHoldingRow("035420", "NAVER", "2", "", "200000", "180000", "0")),
                List.of(new KisBalanceSummaryRow("100000", "500000", "")),
                List.of()
        );

        PortfolioHolding holding = snapshot.holdings().get(0);
        assertThat(holding.marketValueKrw()).isEqualTo(400000d);
        assertThat(holding.weight()).isEqualTo(0.8d);
        assertThat(holding.averagePrice()).isEqualTo(180000d);
        assertThat(holding.unrealizedPnlKrw()).isEqualTo(0d);
    }

    @Test
    void derivesTotalValueFromFallbackMarketValueAndCashWhenSummaryStockValueIsMissing() {
        PortfolioSnapshot snapshot = mapper.toSnapshot(
                List.of(new KisBalanceHoldingRow("035420", "NAVER", "2", "", "200000")),
                List.of(new KisBalanceSummaryRow("100000", "", "")),
                List.of()
        );

        assertThat(snapshot.totalValueKrw()).isEqualTo(500000d);
        assertThat(snapshot.cashWeight()).isEqualTo(0.2d);
        assertThat(snapshot.holdings().get(0).weight()).isEqualTo(0.8d);
    }

    @Test
    void keepsUnrealizedProfitLossNullOnlyWhenKisFieldIsMissing() {
        PortfolioSnapshot snapshot = mapper.toSnapshot(
                List.of(new KisBalanceHoldingRow("035420", "NAVER", "2", "400000", "200000")),
                List.of(new KisBalanceSummaryRow("100000", "500000", "400000")),
                List.of()
        );

        assertThat(snapshot.holdings().get(0).unrealizedPnlKrw()).isNull();
    }
}
