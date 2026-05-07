package com.libra.api.integration.kis;

import java.time.Instant;

public record KisStockListing(
        String ticker,
        String companyName,
        String market,
        String standardCode,
        String source,
        Instant loadedAt
) {
}
