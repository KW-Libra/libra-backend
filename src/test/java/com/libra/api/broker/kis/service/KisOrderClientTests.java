package com.libra.api.broker.kis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderSide;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import org.junit.jupiter.api.Test;

class KisOrderClientTests {

    @Test
    void liveOrderRequiresExplicitTradingFlag() {
        KisProperties properties = properties(false);
        KisOrderClient client = new KisOrderClient(properties, new KisAuthClient(properties));

        assertThatThrownBy(() -> client.placeCashOrder(new KisOrderRequest(
            KisOrderSide.SELL,
            "005930",
            1,
            BigDecimal.ZERO,
            "01",
            "KRX"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.KIS_TRADING_DISABLED);
    }

    private static KisProperties properties(boolean tradingEnabled) {
        return new KisProperties(
            true,
            tradingEnabled,
            KisProperties.Environment.PAPER,
            "app-key",
            "app-secret",
            "12345678",
            "01",
            "hts-id",
            URI.create("https://openapi.koreainvestment.com:9443"),
            URI.create("https://openapivts.koreainvestment.com:29443")
        );
    }
}
