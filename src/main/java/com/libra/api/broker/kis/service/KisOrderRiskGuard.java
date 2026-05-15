package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KisOrderRiskGuard {

    private static final Set<String> ALLOWED_ORDER_DIVISIONS = Set.of("00", "01");
    private static final Set<String> ALLOWED_EXCHANGE_IDS = Set.of("KRX", "NXT", "SOR");

    private final KisProperties properties;

    public KisOrderRiskGuard(KisProperties properties) {
        this.properties = properties;
    }

    public void validate(KisOrderRequest request) {
        validate(request, KisConnection.fromProperties(properties));
    }

    public void validate(KisOrderRequest request, KisConnection connection) {
        if (request.quantity() < 1) {
            reject("주문 수량은 1 이상이어야 합니다");
        }
        if (request.quantity() > connection.maxOrderQuantity()) {
            reject("주문 수량이 허용 한도를 초과했습니다");
        }
        if (request.price().compareTo(BigDecimal.ZERO) < 0) {
            reject("주문 가격은 0 이상이어야 합니다");
        }
        if (!connection.allowsSymbol(request.symbol())) {
            reject("허용되지 않은 종목입니다");
        }
        if (!ALLOWED_ORDER_DIVISIONS.contains(request.orderDivision())) {
            reject("지원하지 않는 주문 구분입니다");
        }
        if (!ALLOWED_EXCHANGE_IDS.contains(request.exchangeId())) {
            reject("지원하지 않는 거래소 구분입니다");
        }
        if ("01".equals(request.orderDivision()) && request.price().compareTo(BigDecimal.ZERO) != 0) {
            reject("시장가 주문 가격은 0이어야 합니다");
        }
        if ("00".equals(request.orderDivision()) && request.price().compareTo(BigDecimal.ZERO) <= 0) {
            reject("지정가 주문 가격은 0보다 커야 합니다");
        }
        if (request.price().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal orderAmount = request.price().multiply(BigDecimal.valueOf(request.quantity()));
            if (orderAmount.compareTo(connection.maxOrderAmount()) > 0) {
                reject("주문 금액이 허용 한도를 초과했습니다");
            }
        }
    }

    private static void reject(String message) {
        throw new ApiException(ErrorCode.KIS_ORDER_REJECTED, message);
    }
}
