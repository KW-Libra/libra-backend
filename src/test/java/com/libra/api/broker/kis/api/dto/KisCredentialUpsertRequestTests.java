package com.libra.api.broker.kis.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.broker.kis.config.KisProperties;
import org.junit.jupiter.api.Test;

class KisCredentialUpsertRequestTests {

    @Test
    void defaultsMissingProductCodeToDomesticStockProductCode() {
        KisCredentialUpsertRequest request = new KisCredentialUpsertRequest(
            KisProperties.Environment.PAPER,
            false,
            "app-key",
            "app-secret",
            "12345678",
            null,
            null
        );

        assertThat(request.accountProductCode()).isEqualTo("01");
    }

    @Test
    void defaultsBlankProductCodeToDomesticStockProductCode() {
        KisCredentialUpsertRequest request = new KisCredentialUpsertRequest(
            KisProperties.Environment.PAPER,
            false,
            "app-key",
            "app-secret",
            "12345678",
            "  ",
            null
        );

        assertThat(request.accountProductCode()).isEqualTo("01");
    }
}
