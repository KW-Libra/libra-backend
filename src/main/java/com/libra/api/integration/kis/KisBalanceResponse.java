package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KisBalanceResponse(
        @JsonProperty("rt_cd")
        String rtCd,
        @JsonProperty("msg_cd")
        String msgCd,
        String msg1,
        List<KisBalanceHoldingRow> output1,
        List<KisBalanceSummaryRow> output2,
        @JsonProperty("ctx_area_fk100")
        String ctxAreaFk100,
        @JsonProperty("ctx_area_nk100")
        String ctxAreaNk100
) {
}
