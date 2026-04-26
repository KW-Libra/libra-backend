package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisBalanceSummaryRow(
        @JsonProperty("dnca_tot_amt")
        String dncaTotAmt,
        @JsonProperty("tot_evlu_amt")
        String totEvluAmt,
        @JsonProperty("evlu_amt_smtl_amt")
        String evluAmtSmtlAmt
) {
}
