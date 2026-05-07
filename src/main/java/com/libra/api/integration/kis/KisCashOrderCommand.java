package com.libra.api.integration.kis;

import java.math.BigDecimal;

public record KisCashOrderCommand(
        String ticker,
        String side,
        long quantity,
        BigDecimal priceKrw,
        String orderType
) {
}
