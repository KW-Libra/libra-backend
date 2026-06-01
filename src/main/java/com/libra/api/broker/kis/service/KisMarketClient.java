package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.api.dto.KisQuoteResponse;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KisMarketClient {

    private static final String INQUIRE_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String INQUIRE_PRICE_TR_ID = "FHKST01010100";
    private static final String INQUIRE_DAILY_ITEM_CHART_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String INQUIRE_DAILY_ITEM_CHART_TR_ID = "FHKST03010100";
    private static final DateTimeFormatter KIS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int DAILY_CHART_MAX_ATTEMPTS = 3;
    private static final long DAILY_CHART_RETRY_BASE_DELAY_MS = 300L;

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

    public List<Map<String, Object>> dailyOhlcv(
        String symbol,
        String marketCode,
        LocalDate startDate,
        LocalDate endDate,
        KisConnection connection
    ) {
        ensureConfigured(connection);
        String normalizedMarketCode = normalizeMarketCode(marketCode);
        for (int attempt = 1; attempt <= DAILY_CHART_MAX_ATTEMPTS; attempt++) {
            try {
                KisDailyChartEnvelope response = requestDailyChart(
                    symbol,
                    normalizedMarketCode,
                    startDate,
                    endDate,
                    connection
                );
                if (response == null) {
                    throw new ApiException(ErrorCode.KIS_UNAVAILABLE, dailyChartFailureMessage(
                        symbol,
                        normalizedMarketCode,
                        "response is empty"
                    ));
                }
                if (!"0".equals(response.rtCd())) {
                    String detail = kisEnvelopeMessage(response.messageCode(), response.message());
                    if (attempt < DAILY_CHART_MAX_ATTEMPTS && isRetryableKisMessage(response.messageCode(), response.message())) {
                        sleepBeforeDailyChartRetry(attempt);
                        continue;
                    }
                    throw new ApiException(ErrorCode.KIS_UNAVAILABLE, dailyChartFailureMessage(symbol, normalizedMarketCode, detail));
                }
                if (response.output2() == null) {
                    throw new ApiException(ErrorCode.KIS_UNAVAILABLE, dailyChartFailureMessage(
                        symbol,
                        normalizedMarketCode,
                        "output2 is empty"
                    ));
                }
                return normalizeDailyChartRows(response.output2());
            } catch (RestClientResponseException e) {
                if (attempt < DAILY_CHART_MAX_ATTEMPTS && isRetryableHttpError(e)) {
                    sleepBeforeDailyChartRetry(attempt);
                    continue;
                }
                throw new ApiException(
                    ErrorCode.KIS_UNAVAILABLE,
                    dailyChartFailureMessage(symbol, normalizedMarketCode, summarizeKisError(e)),
                    e
                );
            } catch (RestClientException e) {
                if (attempt < DAILY_CHART_MAX_ATTEMPTS) {
                    sleepBeforeDailyChartRetry(attempt);
                    continue;
                }
                throw new ApiException(
                    ErrorCode.KIS_UNAVAILABLE,
                    dailyChartFailureMessage(symbol, normalizedMarketCode, e.getMessage()),
                    e
                );
            }
        }
        throw new ApiException(ErrorCode.KIS_UNAVAILABLE, dailyChartFailureMessage(symbol, normalizedMarketCode, "retry exhausted"));
    }

    private KisDailyChartEnvelope requestDailyChart(
        String symbol,
        String normalizedMarketCode,
        LocalDate startDate,
        LocalDate endDate,
        KisConnection connection
    ) {
        return restClient(connection).get()
            .uri(uriBuilder -> uriBuilder
                .path(INQUIRE_DAILY_ITEM_CHART_PATH)
                .queryParam("FID_COND_MRKT_DIV_CODE", normalizedMarketCode)
                .queryParam("FID_INPUT_ISCD", symbol)
                .queryParam("FID_INPUT_DATE_1", KIS_DATE_FORMAT.format(startDate))
                .queryParam("FID_INPUT_DATE_2", KIS_DATE_FORMAT.format(endDate))
                .queryParam("FID_PERIOD_DIV_CODE", "D")
                .queryParam("FID_ORG_ADJ_PRC", "1")
                .build())
            .headers(headers -> applyKisHeaders(headers, authClient.accessToken(connection), INQUIRE_DAILY_ITEM_CHART_TR_ID, connection))
            .retrieve()
            .body(KisDailyChartEnvelope.class);
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

    private static List<Map<String, Object>> normalizeDailyChartRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String date = stringValue(row, "stck_bsop_date");
            if (date.length() != 8) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8));
            item.put("open", decimalText(row, "stck_oprc"));
            item.put("high", decimalText(row, "stck_hgpr"));
            item.put("low", decimalText(row, "stck_lwpr"));
            item.put("close", decimalText(row, "stck_clpr"));
            item.put("volume", decimalText(row, "acml_vol"));
            normalized.add(item);
        }
        normalized.sort(Comparator.comparing(item -> String.valueOf(item.get("date"))));
        return normalized;
    }

    private static String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String decimalText(Map<String, Object> row, String key) {
        return stringValue(row, key).replace(",", "");
    }

    private static boolean isRetryableHttpError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static boolean isRetryableKisMessage(String messageCode, String message) {
        String text = ((messageCode == null ? "" : messageCode) + " " + (message == null ? "" : message))
            .toLowerCase(Locale.ROOT);
        return text.contains("egw")
            || text.contains("rate")
            || text.contains("limit")
            || text.contains("timeout")
            || text.contains("temporary")
            || text.contains("초당")
            || text.contains("제한")
            || text.contains("과다");
    }

    private static void sleepBeforeDailyChartRetry(int attempt) {
        try {
            Thread.sleep(DAILY_CHART_RETRY_BASE_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS daily chart retry interrupted", e);
        }
    }

    private static String dailyChartFailureMessage(String symbol, String marketCode, String detail) {
        String safeDetail = detail == null || detail.isBlank() ? "unknown error" : detail;
        return "KIS daily chart request failed for " + symbol + " (" + marketCode + "): " + safeDetail;
    }

    private static String kisEnvelopeMessage(String messageCode, String message) {
        String safeCode = messageCode == null ? "" : messageCode.trim();
        String safeMessage = message == null ? "" : message.trim();
        if (safeCode.isBlank()) {
            return safeMessage.isBlank() ? "KIS returned failure without message" : safeMessage;
        }
        if (safeMessage.isBlank()) {
            return "msg_cd=" + safeCode;
        }
        return "msg_cd=" + safeCode + " msg=" + safeMessage;
    }

    private static String summarizeKisError(RestClientResponseException e) {
        String body = sanitizeKisErrorBody(e.getResponseBodyAsString());
        String status = "HTTP " + e.getStatusCode().value();
        if (body.isBlank()) {
            return status;
        }
        return status + " body=" + body;
    }

    private static String sanitizeKisErrorBody(String body) {
        if (body == null) {
            return "";
        }
        String sanitized = body
            .replaceAll("(?i)\"(access_token|appkey|appsecret|secretkey|approval_key)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***\"")
            .replaceAll("\\s+", " ")
            .trim();
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 500) + "...";
        }
        return sanitized;
    }

    private record KisEnvelope(
        @com.fasterxml.jackson.annotation.JsonProperty("rt_cd") String rtCd,
        @com.fasterxml.jackson.annotation.JsonProperty("msg_cd") String messageCode,
        @com.fasterxml.jackson.annotation.JsonProperty("msg1") String message,
        Map<String, Object> output
    ) {
    }

    private record KisDailyChartEnvelope(
        @com.fasterxml.jackson.annotation.JsonProperty("rt_cd") String rtCd,
        @com.fasterxml.jackson.annotation.JsonProperty("msg_cd") String messageCode,
        @com.fasterxml.jackson.annotation.JsonProperty("msg1") String message,
        @com.fasterxml.jackson.annotation.JsonProperty("output2") List<Map<String, Object>> output2
    ) {
    }
}
