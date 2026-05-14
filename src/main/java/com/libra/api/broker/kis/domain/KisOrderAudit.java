package com.libra.api.broker.kis.domain;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderResponse;
import com.libra.api.broker.kis.api.dto.KisOrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "kis_order_audits")
@EntityListeners(AuditingEntityListener.class)
public class KisOrderAudit {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String provider = "KIS";

    @Column(nullable = false, length = 16)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KisOrderAuditStatus status = KisOrderAuditStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private KisOrderSide side;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    @Column(name = "order_division", nullable = false, length = 8)
    private String orderDivision;

    @Column(name = "exchange_id", nullable = false, length = 16)
    private String exchangeId;

    @Column(name = "trading_enabled", nullable = false)
    private boolean tradingEnabled;

    @Column(name = "broker_order_no", length = 64)
    private String brokerOrderNo;

    @Column(name = "broker_message", length = 500)
    private String brokerMessage;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "request_json", nullable = false, columnDefinition = "text")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;

    @Column(name = "trace_id", length = 80)
    private String traceId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KisOrderAudit() {
        // JPA
    }

    public static KisOrderAudit requested(
        UUID userId,
        String environment,
        boolean tradingEnabled,
        KisOrderRequest request,
        String requestJson,
        String traceId
    ) {
        KisOrderAudit audit = new KisOrderAudit();
        audit.userId = userId;
        audit.environment = environment;
        audit.status = KisOrderAuditStatus.REQUESTED;
        audit.side = request.side();
        audit.symbol = request.symbol();
        audit.quantity = request.quantity();
        audit.price = request.price();
        audit.orderDivision = request.orderDivision();
        audit.exchangeId = request.exchangeId();
        audit.tradingEnabled = tradingEnabled;
        audit.requestJson = requestJson;
        audit.traceId = traceId;
        return audit;
    }

    public void markCompleted(KisOrderResponse response, String responseJson) {
        this.status = KisOrderAuditStatus.SUBMITTED;
        this.brokerMessage = truncate(response.message(), 500);
        this.brokerOrderNo = truncate(firstRawValue(response.raw(), "ODNO", "odno", "ord_no"), 64);
        this.responseJson = responseJson;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markRejected(String errorCode, String errorMessage, String responseJson) {
        this.status = KisOrderAuditStatus.REJECTED;
        this.errorCode = truncate(errorCode, 64);
        this.errorMessage = truncate(errorMessage, 1000);
        this.responseJson = responseJson;
    }

    public void markFailed(String errorCode, String errorMessage, String responseJson) {
        this.status = KisOrderAuditStatus.FAILED;
        this.errorCode = truncate(errorCode, 64);
        this.errorMessage = truncate(errorMessage, 1000);
        this.responseJson = responseJson;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getEnvironment() { return environment; }
    public KisOrderAuditStatus getStatus() { return status; }
    public KisOrderSide getSide() { return side; }
    public String getSymbol() { return symbol; }
    public long getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public String getOrderDivision() { return orderDivision; }
    public String getExchangeId() { return exchangeId; }
    public boolean isTradingEnabled() { return tradingEnabled; }
    public String getBrokerOrderNo() { return brokerOrderNo; }
    public String getBrokerMessage() { return brokerMessage; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getRequestJson() { return requestJson; }
    public String getResponseJson() { return responseJson; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String firstRawValue(Map<String, Object> raw, String... keys) {
        if (raw == null) {
            return null;
        }
        for (String key : keys) {
            Object value = raw.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
