package com.libra.api.broker.kis.api.dto;

public record KisInstrumentResponse(
    String symbol,
    String marketCode,
    String name,
    String source
) {

    public static KisInstrumentResponse fromQuote(KisQuoteResponse quote) {
        return new KisInstrumentResponse(
            quote.symbol(),
            quote.marketCode(),
            quote.name(),
            "KIS_INQUIRE_PRICE"
        );
    }
}
