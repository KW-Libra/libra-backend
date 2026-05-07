package com.libra.api.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndexRecommenderServiceTest {

    private final IndexRecommenderService service = new IndexRecommenderService();

    @Test
    void recommendsIncomeIndexForDividendPreference() {
        List<IndexRecommendation> result = service.recommend(new IndexRecommendationRequest(
                List.of("KR"),
                "conservative",
                0.85d,
                List.of(),
                List.of()
        ));

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).code()).isEqualTo("KRX_HIDIV");
    }

    @Test
    void fallsBackToEtfWhenCapitalIsBelowDirectIndexingMinimum() {
        List<HoldingSample> result = service.sampleHoldings("KOSPI200", 10_000_000d);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.ticker()).isEqualTo("069500");
            assertThat(item.targetWeight()).isEqualTo(1.0d);
        });
    }
}
