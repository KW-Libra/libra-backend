package com.libra.api.decision;

import java.math.BigDecimal;

public record DecisionExecutionProposalItem(
        String ticker,
        String side,
        long quantity,
        BigDecimal priceKrw,
        BigDecimal amountKrw,
        String orderType,
        double weightDelta,
        String reason
) {
}
