package com.libra.api.broker.kis.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KisBuyableCashResponseTests {

    @Test
    void mapsKisBuyableFieldsToStableResponse() {
        KisBuyableCashResponse response = KisBuyableCashResponse.from(
            "paper",
            "005930",
            new BigDecimal("72000"),
            "01",
            Map.of(
                "nrcvb_buy_amt", "1000000",
                "nrcvb_buy_qty", "13",
                "max_buy_amt", "1500000",
                "max_buy_qty", "20"
            )
        );

        assertThat(response.symbol()).isEqualTo("005930");
        assertThat(response.buyableAmountWithoutMargin()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.buyableQuantityWithoutMargin()).isEqualByComparingTo(new BigDecimal("13"));
        assertThat(response.maxBuyableAmount()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(response.maxBuyableQuantity()).isEqualByComparingTo(new BigDecimal("20"));
    }
}
