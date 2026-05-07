package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record KisHashKeyResponse(
        @JsonProperty("HASH")
        @JsonAlias("hash")
        String hash
) {
}
