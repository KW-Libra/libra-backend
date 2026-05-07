package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisDomesticPriceResponse(
        @JsonProperty("rt_cd")
        String rtCd,
        @JsonProperty("msg_cd")
        String msgCd,
        String msg1,
        KisDomesticPriceOutput output
) {
}
