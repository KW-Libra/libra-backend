package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisBalanceHoldingRow(
        String pdno,
        @JsonProperty("prdt_name")
        String prdtName,
        @JsonProperty("hldg_qty")
        String hldgQty,
        @JsonProperty("evlu_amt")
        String evluAmt,
        String prpr
) {
}
