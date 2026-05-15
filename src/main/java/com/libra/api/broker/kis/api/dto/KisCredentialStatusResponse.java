package com.libra.api.broker.kis.api.dto;

import com.libra.api.broker.kis.domain.KisCredential;
import java.time.Instant;

public record KisCredentialStatusResponse(
    boolean registered,
    String credentialScope,
    boolean enabled,
    boolean tradingEnabled,
    String environment,
    boolean restConfigured,
    boolean accountConfigured,
    boolean webSocketConfigured,
    String maskedAppKey,
    String maskedAccountNumber,
    Instant updatedAt
) {

    public static KisCredentialStatusResponse none() {
        return new KisCredentialStatusResponse(
            false,
            "none",
            false,
            false,
            "paper",
            false,
            false,
            false,
            null,
            null,
            null
        );
    }

    public static KisCredentialStatusResponse from(KisCredential credential, String appKey, String htsId) {
        return new KisCredentialStatusResponse(
            true,
            "user",
            credential.isEnabled(),
            credential.isTradingEnabled(),
            credential.getEnvironment().name().toLowerCase(),
            !appKey.isBlank(),
            !credential.getAccountNumber().isBlank() && !credential.getAccountProductCode().isBlank(),
            !appKey.isBlank() && htsId != null && !htsId.isBlank(),
            mask(appKey),
            maskAccount(credential.getAccountNumber(), credential.getAccountProductCode()),
            credential.getUpdatedAt()
        );
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private static String maskAccount(String accountNumber, String productCode) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return null;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4) + "-" + productCode;
    }
}
