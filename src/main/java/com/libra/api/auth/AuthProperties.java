package com.libra.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "libra.auth")
public record AuthProperties(
    Jwt jwt,
    Refresh refresh,
    Oauth oauth
) {

    public record Jwt(
        @NotBlank String secret,
        @Positive long accessTtlMinutes,
        String issuer
    ) {
    }

    public record Refresh(
        @Positive long ttlDays
    ) {
    }

    public record Oauth(
        @NotBlank String frontendSuccessUri,
        @NotBlank String frontendFailureUri
    ) {
    }
}
