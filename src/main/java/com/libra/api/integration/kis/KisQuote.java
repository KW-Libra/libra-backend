package com.libra.api.integration.kis;

import java.time.OffsetDateTime;

public record KisQuote(
        String ticker,
        String companyName,
        double priceKrw,
        double changeKrw,
        double changeRatePct,
        long volume,
        OffsetDateTime quotedAt,
        String source
) {
}
