package com.libra.api.broker.kis.api.dto;

import com.libra.api.broker.kis.config.KisProperties;

public record KisStatusResponse(
    boolean enabled,
    boolean tradingEnabled,
    String environment,
    String baseUrl,
    boolean restConfigured,
    boolean accountConfigured,
    boolean webSocketConfigured
) {

    public static KisStatusResponse from(KisProperties properties) {
        return new KisStatusResponse(
            properties.enabled(),
            properties.tradingEnabled(),
            properties.environment().name().toLowerCase(),
            properties.baseUrl().toString(),
            properties.hasRestCredentials(),
            properties.hasAccount(),
            properties.hasWebSocketCredentials()
        );
    }
}
