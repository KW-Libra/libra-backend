package com.libra.api.broker.kis.api.dto;

import com.libra.api.broker.kis.config.KisProperties;
import java.math.BigDecimal;

public record KisStatusResponse(
    boolean registered,
    String credentialScope,
    boolean enabled,
    boolean tradingEnabled,
    String environment,
    String baseUrl,
    boolean restConfigured,
    boolean accountConfigured,
    boolean webSocketConfigured,
    String maskedAppKey,
    String maskedAccountNumber,
    long maxOrderQuantity,
    BigDecimal maxOrderAmount,
    boolean symbolAllowListEnabled,
    int allowedSymbolsCount
) {

    public static KisStatusResponse from(KisProperties properties) {
        return new KisStatusResponse(
            properties.enabled() && properties.hasRestCredentials(),
            properties.enabled() && properties.hasRestCredentials() ? "server" : "none",
            properties.enabled(),
            properties.tradingEnabled(),
            properties.environment().name().toLowerCase(),
            properties.baseUrl().toString(),
            properties.hasRestCredentials(),
            properties.hasAccount(),
            properties.hasWebSocketCredentials(),
            null,
            maskAccount(properties.accountNumber(), properties.accountProductCode()),
            properties.maxOrderQuantity(),
            properties.maxOrderAmount(),
            properties.symbolAllowListEnabled(),
            properties.allowedSymbols().size()
        );
    }

    public static KisStatusResponse from(KisCredentialStatusResponse credential, KisProperties properties) {
        return new KisStatusResponse(
            credential.registered(),
            credential.credentialScope(),
            credential.enabled(),
            credential.tradingEnabled(),
            credential.environment(),
            baseUrl(credential.environment(), properties),
            credential.restConfigured(),
            credential.accountConfigured(),
            credential.webSocketConfigured(),
            credential.maskedAppKey(),
            credential.maskedAccountNumber(),
            properties.maxOrderQuantity(),
            properties.maxOrderAmount(),
            properties.symbolAllowListEnabled(),
            properties.allowedSymbols().size()
        );
    }

    private static String baseUrl(String environment, KisProperties properties) {
        if ("prod".equalsIgnoreCase(environment)) {
            return properties.prodBaseUrl().toString();
        }
        return properties.paperBaseUrl().toString();
    }

    private static String maskAccount(String accountNumber, String productCode) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return null;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4) + "-" + productCode;
    }
}
