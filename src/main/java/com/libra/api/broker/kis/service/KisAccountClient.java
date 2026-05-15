package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.api.dto.KisBalanceResponse;
import com.libra.api.broker.kis.api.dto.KisBuyableCashResponse;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KisAccountClient {

    private static final String INQUIRE_BALANCE_PATH = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String INQUIRE_PSBL_ORDER_PATH = "/uapi/domestic-stock/v1/trading/inquire-psbl-order";
    private static final int MAX_BALANCE_PAGES = 10;

    private final KisProperties properties;
    private final KisAuthClient authClient;

    public KisAccountClient(KisProperties properties, KisAuthClient authClient) {
        this.properties = properties;
        this.authClient = authClient;
    }

    public KisBalanceResponse balance() {
        return balance(KisConnection.fromProperties(properties));
    }

    public KisBalanceResponse balance(KisConnection connection) {
        ensureConfigured(connection);
        String environment = connection.environmentName();
        String nextFk = "";
        String nextNk = "";
        String trCont = "";
        boolean hasNext = false;
        Map<String, Object> summary = Map.of();
        List<Map<String, Object>> holdings = new ArrayList<>();

        try {
            for (int page = 0; page < MAX_BALANCE_PAGES; page++) {
                ResponseEntity<KisBalanceEnvelope> responseEntity = requestBalancePage(connection, nextFk, nextNk, trCont);
                KisBalanceEnvelope response = responseEntity.getBody();
                if (response == null) {
                    throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS balance response is empty");
                }
                if (!"0".equals(response.rtCd())) {
                    throw new ApiException(ErrorCode.KIS_UNAVAILABLE, response.message());
                }
                holdings.addAll(response.output1OrEmpty());
                summary = response.firstSummary();
                nextFk = response.contextFk();
                nextNk = response.contextNk();
                trCont = responseEntity.getHeaders().getFirst("tr_cont");
                hasNext = "M".equalsIgnoreCase(trCont) || "F".equalsIgnoreCase(trCont);
                if (!hasNext) {
                    break;
                }
                trCont = "N";
            }
            return KisBalanceResponse.from(environment, holdings, summary, hasNext, nextFk, nextNk);
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS balance request failed");
        }
    }

    public KisBuyableCashResponse buyableCash(String symbol, BigDecimal price, String orderDivision) {
        return buyableCash(symbol, price, orderDivision, KisConnection.fromProperties(properties));
    }

    public KisBuyableCashResponse buyableCash(
        String symbol,
        BigDecimal price,
        String orderDivision,
        KisConnection connection
    ) {
        ensureConfigured(connection);
        BigDecimal normalizedPrice = price == null ? BigDecimal.ZERO : price;
        String normalizedOrderDivision = normalizeOrderDivision(orderDivision);
        try {
            KisSingleOutputEnvelope response = restClient(connection).get()
                .uri(uriBuilder -> uriBuilder
                    .path(INQUIRE_PSBL_ORDER_PATH)
                    .queryParam("CANO", connection.accountNumber())
                    .queryParam("ACNT_PRDT_CD", connection.accountProductCode())
                    .queryParam("PDNO", symbol)
                    .queryParam("ORD_UNPR", normalizedPrice.toPlainString())
                    .queryParam("ORD_DVSN", normalizedOrderDivision)
                    .queryParam("CMA_EVLU_AMT_ICLD_YN", "N")
                    .queryParam("OVRS_ICLD_YN", "N")
                    .build())
                .headers(headers -> applyKisHeaders(headers, authClient.accessToken(connection), buyableCashTrId(connection), "", connection))
                .retrieve()
                .body(KisSingleOutputEnvelope.class);
            if (response == null || response.output() == null) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS buyable cash response is empty");
            }
            if (!"0".equals(response.rtCd())) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, response.message());
            }
            return KisBuyableCashResponse.from(
                connection.environmentName(),
                symbol,
                normalizedPrice,
                normalizedOrderDivision,
                response.output()
            );
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS buyable cash request failed");
        }
    }

    private ResponseEntity<KisBalanceEnvelope> requestBalancePage(
        KisConnection connection,
        String contextFk,
        String contextNk,
        String trCont
    ) {
        return restClient(connection).get()
            .uri(uriBuilder -> uriBuilder
                .path(INQUIRE_BALANCE_PATH)
                .queryParam("CANO", connection.accountNumber())
                .queryParam("ACNT_PRDT_CD", connection.accountProductCode())
                .queryParam("AFHR_FLPR_YN", "N")
                .queryParam("OFL_YN", "")
                .queryParam("INQR_DVSN", "01")
                .queryParam("UNPR_DVSN", "01")
                .queryParam("FUND_STTL_ICLD_YN", "N")
                .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                .queryParam("PRCS_DVSN", "00")
                .queryParam("CTX_AREA_FK100", contextFk)
                .queryParam("CTX_AREA_NK100", contextNk)
                .build())
            .headers(headers -> applyKisHeaders(headers, authClient.accessToken(connection), balanceTrId(connection), trCont, connection))
            .retrieve()
            .toEntity(KisBalanceEnvelope.class);
    }

    private static RestClient restClient(KisConnection connection) {
        return RestClient.builder()
            .baseUrl(connection.baseUrl().toString())
            .build();
    }

    private void ensureConfigured(KisConnection connection) {
        if (!connection.enabled() || !connection.hasRestCredentials() || !connection.hasAccount()) {
            throw new ApiException(ErrorCode.KIS_NOT_CONFIGURED);
        }
    }

    private void applyKisHeaders(
        HttpHeaders headers,
        String token,
        String trId,
        String trCont,
        KisConnection connection
    ) {
        headers.setBearerAuth(token);
        headers.set("appkey", connection.appKey());
        headers.set("appsecret", connection.appSecret());
        headers.set("tr_id", trId);
        headers.set("custtype", "P");
        if (trCont != null && !trCont.isBlank()) {
            headers.set("tr_cont", trCont);
        }
    }

    private String balanceTrId(KisConnection connection) {
        return connection.environment() == KisProperties.Environment.PAPER ? "VTTC8434R" : "TTTC8434R";
    }

    private String buyableCashTrId(KisConnection connection) {
        return connection.environment() == KisProperties.Environment.PAPER ? "VTTC8908R" : "TTTC8908R";
    }

    private static String normalizeOrderDivision(String orderDivision) {
        if (orderDivision == null || orderDivision.isBlank()) {
            return "01";
        }
        return orderDivision.trim();
    }

    private record KisBalanceEnvelope(
        @com.fasterxml.jackson.annotation.JsonProperty("rt_cd") String rtCd,
        @com.fasterxml.jackson.annotation.JsonProperty("msg_cd") String messageCode,
        @com.fasterxml.jackson.annotation.JsonProperty("msg1") String message,
        List<Map<String, Object>> output1,
        List<Map<String, Object>> output2,
        @com.fasterxml.jackson.annotation.JsonProperty("ctx_area_fk100") String contextFk,
        @com.fasterxml.jackson.annotation.JsonProperty("ctx_area_nk100") String contextNk
    ) {

        List<Map<String, Object>> output1OrEmpty() {
            return output1 == null ? List.of() : output1;
        }

        Map<String, Object> firstSummary() {
            if (output2 == null || output2.isEmpty() || output2.getFirst() == null) {
                return Map.of();
            }
            return new LinkedHashMap<>(output2.getFirst());
        }
    }

    private record KisSingleOutputEnvelope(
        @com.fasterxml.jackson.annotation.JsonProperty("rt_cd") String rtCd,
        @com.fasterxml.jackson.annotation.JsonProperty("msg_cd") String messageCode,
        @com.fasterxml.jackson.annotation.JsonProperty("msg1") String message,
        Map<String, Object> output
    ) {
    }
}
