package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisDomesticPriceOutput(
        @JsonProperty("stck_shrn_iscd")
        String ticker,
        @JsonProperty("hts_kor_isnm")
        String companyName,
        @JsonProperty("stck_prpr")
        String currentPrice,
        @JsonProperty("prdy_vrss")
        String previousDayChange,
        @JsonProperty("prdy_vrss_sign")
        String previousDayChangeSign,
        @JsonProperty("prdy_ctrt")
        String previousDayChangeRate,
        @JsonProperty("acml_vol")
        String accumulatedVolume,
        @JsonProperty("stck_oprc")
        String openPrice,
        @JsonProperty("stck_hgpr")
        String highPrice,
        @JsonProperty("stck_lwpr")
        String lowPrice
) {
}
