package com.libra.api.broker.kis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderResponse;
import com.libra.api.broker.kis.api.dto.KisOrderSide;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KisOrderAuditTests {

    @Test
    void requestedAuditCapturesNormalizedOrderIntent() {
        KisOrderRequest request = new KisOrderRequest(
            KisOrderSide.BUY,
            "005930",
            3,
            BigDecimal.ZERO,
            null,
            null
        );

        KisOrderAudit audit = KisOrderAudit.requested(
            UUID.randomUUID(),
            "paper",
            false,
            request,
            "{}",
            "trace-1"
        );

        assertThat(audit.getStatus()).isEqualTo(KisOrderAuditStatus.REQUESTED);
        assertThat(audit.getOrderDivision()).isEqualTo("01");
        assertThat(audit.getExchangeId()).isEqualTo("KRX");
        assertThat(audit.isTradingEnabled()).isFalse();
        assertThat(audit.getTraceId()).isEqualTo("trace-1");
    }

    @Test
    void completedSubmittedOrderStoresBrokerOrderNumber() {
        KisOrderRequest request = new KisOrderRequest(
            KisOrderSide.BUY,
            "005930",
            1,
            new BigDecimal("70000"),
            "00",
            "KRX"
        );
        KisOrderAudit audit = KisOrderAudit.requested(
            UUID.randomUUID(),
            "paper",
            true,
            request,
            "{}",
            "trace-1"
        );

        audit.markCompleted(KisOrderResponse.submitted(
            "paper",
            request,
            "submitted",
            Map.of("ODNO", "1234567890")
        ), "{}");

        assertThat(audit.getStatus()).isEqualTo(KisOrderAuditStatus.SUBMITTED);
        assertThat(audit.getBrokerOrderNo()).isEqualTo("1234567890");
        assertThat(audit.getBrokerMessage()).isEqualTo("submitted");
    }

    @Test
    void failedAuditStoresErrorCode() {
        KisOrderRequest request = new KisOrderRequest(
            KisOrderSide.BUY,
            "005930",
            1,
            BigDecimal.ZERO,
            null,
            null
        );
        KisOrderAudit audit = KisOrderAudit.requested(
            UUID.randomUUID(),
            "paper",
            false,
            request,
            "{}",
            "trace-1"
        );

        audit.markRejected("KIS_TRADING_DISABLED", "disabled", "{}");

        assertThat(audit.getStatus()).isEqualTo(KisOrderAuditStatus.REJECTED);
        assertThat(audit.getErrorCode()).isEqualTo("KIS_TRADING_DISABLED");
        assertThat(audit.getErrorMessage()).isEqualTo("disabled");
    }
}
