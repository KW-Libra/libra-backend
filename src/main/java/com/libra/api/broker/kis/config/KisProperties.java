package com.libra.api.broker.kis.config;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.kis")
public record KisProperties(
    boolean enabled,
    boolean tradingEnabled,
    Environment environment,
    String appKey,
    String appSecret,
    String accountNumber,
    String accountProductCode,
    String htsId,
    URI prodBaseUrl,
    URI paperBaseUrl,
    long maxOrderQuantity,
    BigDecimal maxOrderAmount,
    List<String> allowedSymbols,
    String credentialEncryptionKey
) {

    public enum Environment {
        PAPER,
        PROD
    }

    public KisProperties {
        if (environment == null) {
            environment = Environment.PAPER;
        }
        if (appKey == null) {
            appKey = "";
        }
        if (appSecret == null) {
            appSecret = "";
        }
        if (accountNumber == null) {
            accountNumber = "";
        }
        if (accountProductCode == null || accountProductCode.isBlank()) {
            accountProductCode = "01";
        }
        if (htsId == null) {
            htsId = "";
        }
        if (prodBaseUrl == null) {
            prodBaseUrl = URI.create("https://openapi.koreainvestment.com:9443");
        }
        if (paperBaseUrl == null) {
            paperBaseUrl = URI.create("https://openapivts.koreainvestment.com:29443");
        }
        if (maxOrderQuantity <= 0) {
            maxOrderQuantity = 1000;
        }
        if (maxOrderAmount == null || maxOrderAmount.signum() <= 0) {
            maxOrderAmount = new BigDecimal("10000000");
        }
        if (allowedSymbols == null) {
            allowedSymbols = List.of();
        } else {
            allowedSymbols = allowedSymbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        }
        if (credentialEncryptionKey == null) {
            credentialEncryptionKey = "";
        }
    }

    public URI baseUrl() {
        return environment == Environment.PROD ? prodBaseUrl : paperBaseUrl;
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
        return symbol != null && allowedSymbols.contains(symbol.trim().toUpperCase(Locale.ROOT));
    }

    public boolean hasCredentialEncryptionKey() {
        return !credentialEncryptionKey.isBlank();
    }
}
