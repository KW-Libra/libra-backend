package com.libra.api.broker.kis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class KisCredentialEncryptionServiceTests {

    @Test
    void encryptsAndDecryptsCredentialValue() {
        KisCredentialEncryptionService service = new KisCredentialEncryptionService(properties("test-credential-encryption-key-32"));

        String encrypted = service.encrypt("secret-value");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("secret-value");
        assertThat(service.decrypt(encrypted)).isEqualTo("secret-value");
    }

    @Test
    void encryptionRequiresConfiguredKey() {
        KisCredentialEncryptionService service = new KisCredentialEncryptionService(properties(""));

        assertThatThrownBy(() -> service.encrypt("secret-value"))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.KIS_CREDENTIAL_ENCRYPTION_NOT_CONFIGURED);
    }

    private static KisProperties properties(String encryptionKey) {
        return new KisProperties(
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
            List.of(),
            encryptionKey
        );
    }
}
