package com.libra.api.integration.kis;

import java.time.Instant;

public record KisCredentialStatus(
        boolean configured,
        String environment,
        String appKeyMasked,
        String accountNoMasked,
        String productCode,
        Instant updatedAt
) {
    public static KisCredentialStatus empty() {
        return new KisCredentialStatus(false, null, null, null, null, null);
    }
}
