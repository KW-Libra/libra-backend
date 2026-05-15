package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.api.dto.KisOrderRequest;
import com.libra.api.broker.kis.api.dto.KisOrderResponse;
import com.libra.api.broker.kis.api.dto.KisOrderSide;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KisOrderClient {

    private static final String ORDER_CASH_PATH = "/uapi/domestic-stock/v1/trading/order-cash";

    private final KisProperties properties;
    private final KisAuthClient authClient;
    private final KisOrderRiskGuard riskGuard;

    public KisOrderClient(KisProperties properties, KisAuthClient authClient, KisOrderRiskGuard riskGuard) {
        this.properties = properties;
        this.authClient = authClient;
        this.riskGuard = riskGuard;
    }

    public KisOrderResponse placeCashOrder(KisOrderRequest request) {
        return placeCashOrder(request, KisConnection.fromProperties(properties));
    }

    public KisOrderResponse placeCashOrder(KisOrderRequest request, KisConnection connection) {
        riskGuard.validate(request, connection);
        ensureTradingEnabled(connection);

        Map<String, String> body = orderBody(request, connection);
        String trId = orderTrId(request.side(), connection);
        try {
            KisOrderEnvelope response = restClient(connection).post()
                .uri(ORDER_CASH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyOrderHeaders(headers, authClient.accessToken(connection), trId, body, connection))
                .body(body)
                .retrieve()
                .body(KisOrderEnvelope.class);
            if (response == null || response.output() == null) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS order response is empty");
            }
            if (!"0".equals(response.rtCd())) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, response.message());
            }
            return KisOrderResponse.submitted(
                connection.environmentName(),
                request,
                response.message(),
                response.output()
            );
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS order request failed");
        }
    }

    private static RestClient restClient(KisConnection connection) {
        return RestClient.builder()
            .baseUrl(connection.baseUrl().toString())
            .build();
    }

    private void ensureTradingEnabled(KisConnection connection) {
        if (!connection.enabled() || !connection.hasRestCredentials() || !connection.hasAccount()) {
            throw new ApiException(ErrorCode.KIS_NOT_CONFIGURED);
        }
        if (!connection.tradingEnabled()) {
            throw new ApiException(ErrorCode.KIS_TRADING_DISABLED);
        }
    }

    private Map<String, String> orderBody(KisOrderRequest request, KisConnection connection) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("CANO", connection.accountNumber());
        body.put("ACNT_PRDT_CD", connection.accountProductCode());
        body.put("PDNO", request.symbol());
        body.put("ORD_DVSN", request.orderDivision());
        body.put("ORD_QTY", Long.toString(request.quantity()));
        body.put("ORD_UNPR", request.price().toPlainString());
        body.put("EXCG_ID_DVSN_CD", request.exchangeId());
        body.put("SLL_TYPE", request.side() == KisOrderSide.SELL ? "01" : "");
        body.put("CNDT_PRIC", "");
        return body;
    }

    private String orderTrId(KisOrderSide side, KisConnection connection) {
        boolean paper = connection.environment() == KisProperties.Environment.PAPER;
        if (side == KisOrderSide.BUY) {
            return paper ? "VTTC0012U" : "TTTC0012U";
        }
        return paper ? "VTTC0011U" : "TTTC0011U";
    }

    private void applyOrderHeaders(
        HttpHeaders headers,
        String token,
        String trId,
        Map<String, String> body,
        KisConnection connection
    ) {
        headers.setBearerAuth(token);
        headers.set("appkey", connection.appKey());
        headers.set("appsecret", connection.appSecret());
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
        headers.set("hashkey", authClient.hashKey(connection, body));
    }

    private record KisOrderEnvelope(
        @com.fasterxml.jackson.annotation.JsonProperty("rt_cd") String rtCd,
        @com.fasterxml.jackson.annotation.JsonProperty("msg_cd") String messageCode,
        @com.fasterxml.jackson.annotation.JsonProperty("msg1") String message,
        Map<String, Object> output
    ) {
    }
}
