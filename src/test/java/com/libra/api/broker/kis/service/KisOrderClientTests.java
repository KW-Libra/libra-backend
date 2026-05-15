package com.libra.api.broker.kis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderSide;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class KisOrderClientTests {

    @Test
    void liveOrderRequiresExplicitTradingFlag() {
        KisProperties properties = properties(false);
        KisOrderClient client = new KisOrderClient(
            properties,
            new KisAuthClient(properties),
            new KisOrderRiskGuard(properties)
        );

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

    @Test
    void orderGuardRejectsLimitOrderOverConfiguredAmount() {
        KisProperties properties = properties(true);
        KisOrderClient client = new KisOrderClient(
            properties,
            new KisAuthClient(properties),
            new KisOrderRiskGuard(properties)
        );

        assertThatThrownBy(() -> client.placeCashOrder(new KisOrderRequest(
            KisOrderSide.BUY,
            "005930",
            200,
            new BigDecimal("70000"),
            "00",
            "KRX"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.KIS_ORDER_REJECTED);
    }

    @Test
    void orderGuardRejectsUnsupportedOrderDivisionBeforeBrokerCall() {
        KisProperties properties = properties(true);
        KisOrderClient client = new KisOrderClient(
            properties,
            new KisAuthClient(properties),
            new KisOrderRiskGuard(properties)
        );

        assertThatThrownBy(() -> client.placeCashOrder(new KisOrderRequest(
            KisOrderSide.BUY,
            "005930",
            1,
            BigDecimal.ZERO,
            "99",
            "KRX"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.KIS_ORDER_REJECTED);
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
            URI.create("https://openapivts.koreainvestment.com:29443"),
            1000,
            new BigDecimal("10000000"),
            List.of()
        );
    }
}
