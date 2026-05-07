package com.libra.api.integration.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record KisCashOrderResponse(
        @JsonProperty("rt_cd")
        String rtCd,
        @JsonProperty("msg_cd")
        String msgCd,
        @JsonProperty("msg1")
        String msg1,
        Map<String, Object> output
) {
}
