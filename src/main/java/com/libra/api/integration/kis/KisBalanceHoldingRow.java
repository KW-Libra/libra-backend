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
        String prpr,
        @JsonProperty("pchs_avg_pric")
        String pchsAvgPric,
        @JsonProperty("evlu_pfls_amt")
        String evluPflsAmt
) {
    public KisBalanceHoldingRow(
            String pdno,
            String prdtName,
            String hldgQty,
            String evluAmt,
            String prpr
    ) {
        this(pdno, prdtName, hldgQty, evluAmt, prpr, null, null);
    }
}
