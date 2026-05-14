package com.libra.api.broker.kis.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KisQuoteResponseTests {

    @Test
    void mapsKisOutputIntoStableQuoteShape() {
        KisQuoteResponse quote = KisQuoteResponse.from("005930", "J", Map.of(
            "hts_kor_isnm", "삼성전자",
            "stck_prpr", "72000",
            "prdy_vrss", "-500",
            "prdy_ctrt", "-0.69",
            "acml_vol", "1234567"
        ));

        assertThat(quote.symbol()).isEqualTo("005930");
        assertThat(quote.marketCode()).isEqualTo("J");
        assertThat(quote.name()).isEqualTo("삼성전자");
        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("72000"));
        assertThat(quote.change()).isEqualByComparingTo(new BigDecimal("-500"));
        assertThat(quote.changeRate()).isEqualByComparingTo(new BigDecimal("-0.69"));
        assertThat(quote.accumulatedVolume()).isEqualTo(1_234_567L);
    }
}
