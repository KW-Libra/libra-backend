package com.libra.api.decision;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record DecisionExecutionResult(
        Long id,
        String decisionRunId,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal priceKrw,
        BigDecimal amountKrw,
        LocalDateTime executedAt,
        String status,
        String message,
        String orderNo,
        Map<String, Object> rawPayload,
        LocalDateTime createdAt
) {
    public static DecisionExecutionResult from(DecisionExecutionEntity entity, Map<String, Object> rawPayload) {
        return new DecisionExecutionResult(
                entity.getId(),
                entity.getDecisionRunId(),
                entity.getTicker(),
                entity.getSide(),
                entity.getQuantity(),
                entity.getPriceKrw(),
                entity.getAmountKrw(),
                entity.getExecutedAt(),
                entity.getStatus(),
                text(rawPayload, "message"),
                text(rawPayload, "order_no"),
                rawPayload,
                entity.getCreatedAt()
        );
    }

    private static String text(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
