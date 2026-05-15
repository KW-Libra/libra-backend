package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.config.KisProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record KisConnection(
    String credentialScope,
    boolean enabled,
    boolean tradingEnabled,
    KisProperties.Environment environment,
    String appKey,
    String appSecret,
    String accountNumber,
    String accountProductCode,
    String htsId,
    URI baseUrl,
    long maxOrderQuantity,
    BigDecimal maxOrderAmount,
    List<String> allowedSymbols
) {

    public static KisConnection fromProperties(KisProperties properties) {
        return new KisConnection(
            "server",
            properties.enabled(),
            properties.tradingEnabled(),
            properties.environment(),
            properties.appKey(),
            properties.appSecret(),
            properties.accountNumber(),
            properties.accountProductCode(),
            properties.htsId(),
            properties.baseUrl(),
            properties.maxOrderQuantity(),
            properties.maxOrderAmount(),
            properties.allowedSymbols()
        );
    }

    public boolean hasRestCredentials() {
        return !appKey.isBlank() && !appSecret.isBlank();
    }

    public boolean hasAccount() {
        return !accountNumber.isBlank() && !accountProductCode.isBlank();
    }

    public boolean hasWebSocketCredentials() {
        return hasRestCredentials() && !htsId.isBlank();
    }

    public boolean symbolAllowListEnabled() {
        return !allowedSymbols.isEmpty();
    }

    public boolean allowsSymbol(String symbol) {
        if (!symbolAllowListEnabled()) {
            return true;
        }
        return symbol != null && allowedSymbols.contains(symbol.trim().toUpperCase());
    }

    public String environmentName() {
        return environment.name().toLowerCase();
    }
}
