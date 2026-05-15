package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.api.dto.KisQuoteResponse;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KisMarketClient {

    private static final String INQUIRE_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String INQUIRE_PRICE_TR_ID = "FHKST01010100";

    private final KisProperties properties;
    private final KisAuthClient authClient;

    public KisMarketClient(KisProperties properties, KisAuthClient authClient) {
        this.properties = properties;
        this.authClient = authClient;
    }

    public KisQuoteResponse quote(String symbol, String marketCode) {
        return quote(symbol, marketCode, KisConnection.fromProperties(properties));
    }

    public KisQuoteResponse quote(String symbol, String marketCode, KisConnection connection) {
        ensureConfigured(connection);
        String normalizedMarketCode = normalizeMarketCode(marketCode);
        try {
            KisEnvelope response = restClient(connection).get()
                .uri(uriBuilder -> uriBuilder
                    .path(INQUIRE_PRICE_PATH)
                    .queryParam("FID_COND_MRKT_DIV_CODE", normalizedMarketCode)
                    .queryParam("FID_INPUT_ISCD", symbol)
                    .build())
                .headers(headers -> applyKisHeaders(headers, authClient.accessToken(connection), INQUIRE_PRICE_TR_ID, connection))
                .retrieve()
                .body(KisEnvelope.class);
            if (response == null || response.output() == null) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS quote response is empty");
            }
            if (!"0".equals(response.rtCd())) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, response.message());
            }
            return KisQuoteResponse.from(symbol, normalizedMarketCode, response.output());
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS quote request failed");
        }
    }

    private static RestClient restClient(KisConnection connection) {
        return RestClient.builder()
            .baseUrl(connection.baseUrl().toString())
            .build();
    }

    private void ensureConfigured(KisConnection connection) {
        if (!connection.enabled() || !connection.hasRestCredentials()) {
            throw new ApiException(ErrorCode.KIS_NOT_CONFIGURED);
        }
    }

    private void applyKisHeaders(HttpHeaders headers, String token, String trId, KisConnection connection) {
        headers.setBearerAuth(token);
        headers.set("appkey", connection.appKey());
        headers.set("appsecret", connection.appSecret());
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
    }

    private static String normalizeMarketCode(String marketCode) {
        if (marketCode == null || marketCode.isBlank()) {
            return "J";
        }
        return marketCode.trim().toUpperCase();
    }

    private record KisEnvelope(
        @com.fasterxml.jackson.annotation.JsonProperty("rt_cd") String rtCd,
        @com.fasterxml.jackson.annotation.JsonProperty("msg_cd") String messageCode,
        @com.fasterxml.jackson.annotation.JsonProperty("msg1") String message,
        Map<String, Object> output
    ) {
    }
}
