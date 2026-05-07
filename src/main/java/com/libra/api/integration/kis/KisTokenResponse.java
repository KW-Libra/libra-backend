package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("expires_in")
        Long expiresIn,
        @JsonProperty("access_token_token_expired")
        String accessTokenExpiredAt
) {
}
