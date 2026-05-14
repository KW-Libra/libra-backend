package com.libra.api.broker.kis.config;

import java.net.URI;
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
    URI paperBaseUrl
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
}
