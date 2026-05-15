package com.libra.api.broker.kis.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class KisAccountClientTests {

    @Test
    void balanceRequiresKisAccountConfiguration() {
        KisProperties properties = new KisProperties(
            false,
            false,
            KisProperties.Environment.PAPER,
            "",
            "",
            "",
            "01",
            "",
            URI.create("https://openapi.koreainvestment.com:9443"),
            URI.create("https://openapivts.koreainvestment.com:29443"),
            1000,
            new BigDecimal("10000000"),
            List.of()
        );
        KisAccountClient client = new KisAccountClient(properties, new KisAuthClient(properties));

        assertThatThrownBy(client::balance)
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.KIS_NOT_CONFIGURED);
    }
}
