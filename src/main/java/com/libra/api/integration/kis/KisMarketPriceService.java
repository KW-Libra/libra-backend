package com.libra.api.integration.kis;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class KisMarketPriceService {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String DOMESTIC_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final long QUOTE_REQUEST_INTERVAL_MS = 1100L;

    private final KisProperties properties;
    private final KisAccessTokenService accessTokenService;

    public KisMarketPriceService(KisProperties properties, KisAccessTokenService accessTokenService) {
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    public List<KisQuote> fetchDomesticQuotes(String environment, List<String> tickers) {
        return fetchDomesticQuotes(environment, tickers, null);
    }

    public List<KisQuote> fetchDomesticQuotes(
            String environment,
            List<String> tickers,
            KisProperties.Credential storedCredential
    ) {
        List<String> normalizedTickers = tickers.stream()
                .map(this::normalizeTicker)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedTickers.isEmpty()) {
            throw new IllegalArgumentException("at least one 6 digit ticker is required");
        }

        KisProperties.Credential credential = storedCredential == null
                ? selectCredential(environment)
                : storedCredential;
        validateCredential(credential, environment);

        RestClient restClient = buildRestClient(credential);
        String accessToken = accessTokenService.issueAccessToken(credential);
        OffsetDateTime quotedAt = OffsetDateTime.now();

        List<KisQuote> quotes = new java.util.ArrayList<>(normalizedTickers.size());
        for (int index = 0; index < normalizedTickers.size(); index++) {
            if (index > 0) {
                waitForQuoteRateLimit();
            }
            quotes.add(fetchDomesticQuote(restClient, credential, accessToken, normalizedTickers.get(index), quotedAt));
        }
        return quotes;
    }

    private KisQuote fetchDomesticQuote(
            RestClient restClient,
            KisProperties.Credential credential,
            String accessToken,
            String ticker,
            OffsetDateTime quotedAt
    ) {
        try {
            KisDomesticPriceResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DOMESTIC_PRICE_PATH)
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", ticker)
                            .build())
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("appkey", credential.getAppKey());
                        headers.set("appsecret", credential.getAppSecret());
                        headers.set("tr_id", "FHKST01010100");
                        headers.set("custtype", "P");
                    })
                    .retrieve()
                    .body(KisDomesticPriceResponse.class);
            if (response == null || response.output() == null) {
                throw new KisPortfolioSyncException("KIS price response body was empty.");
            }
            if (!"0".equals(response.rtCd())) {
                throw new KisPortfolioSyncException(
                        "KIS price inquiry failed: " + response.msgCd() + " " + response.msg1()
                );
            }
            return toQuote(ticker, response.output(), quotedAt);
        } catch (RestClientResponseException exception) {
            throw new KisPortfolioSyncException(
                    "Failed to fetch KIS current price for " + ticker + ": " + sanitizeBody(exception.getResponseBodyAsString()),
                    exception
            );
        } catch (RestClientException exception) {
            throw new KisPortfolioSyncException("Failed to fetch KIS current price for " + ticker + ".", exception);
        }
    }

    private KisQuote toQuote(String fallbackTicker, KisDomesticPriceOutput output, OffsetDateTime quotedAt) {
        String ticker = StringUtils.hasText(output.ticker()) ? normalizeTicker(output.ticker()) : fallbackTicker;
        double unsignedChange = Math.abs(parseDouble(output.previousDayChange()));
        double change = signedChange(unsignedChange, output.previousDayChangeSign());
        return new KisQuote(
                ticker,
                StringUtils.hasText(output.companyName()) ? output.companyName().trim() : ticker,
                parseDouble(output.currentPrice()),
                change,
                signedChange(Math.abs(parseDouble(output.previousDayChangeRate())), output.previousDayChangeSign()),
                parseLong(output.accumulatedVolume()),
                quotedAt,
                "KIS_DOMESTIC_INQUIRE_PRICE"
        );
    }

    private KisProperties.Credential selectCredential(String environment) {
        return "demo".equalsIgnoreCase(environment) ? properties.getDemo() : properties.getReal();
    }

    private void validateCredential(KisProperties.Credential credential, String environment) {
        String envName = StringUtils.hasText(environment) ? environment : "real";
        if (!StringUtils.hasText(credential.getBaseUrl())) {
            throw new KisPortfolioSyncException("KIS " + envName + " base URL is missing.");
        }
        if (!StringUtils.hasText(credential.getAppKey())) {
            throw new KisPortfolioSyncException("KIS " + envName + " app key is missing.");
        }
        if (!StringUtils.hasText(credential.getAppSecret())) {
            throw new KisPortfolioSyncException("KIS " + envName + " app secret is missing.");
        }
    }

    private RestClient buildRestClient(KisProperties.Credential credential) {
        RestClient.Builder builder = RestClient.builder().baseUrl(credential.getBaseUrl());
        if (StringUtils.hasText(credential.getUserAgent())) {
            builder.defaultHeader(HttpHeaders.USER_AGENT, credential.getUserAgent());
        }
        return builder.build();
    }

    private String issueAccessToken(RestClient restClient, KisProperties.Credential credential) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("grant_type", "client_credentials");
        payload.put("appkey", credential.getAppKey());
        payload.put("appsecret", credential.getAppSecret());

        try {
            KisTokenResponse response = restClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(KisTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new KisPortfolioSyncException("KIS token issuance returned an empty access token.");
            }
            return response.accessToken();
        } catch (RestClientException exception) {
            throw new KisPortfolioSyncException("Failed to issue KIS access token.", exception);
        }
    }

    private String normalizeTicker(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (!digits.matches("\\d{6}")) {
            throw new IllegalArgumentException("ticker must be a 6 digit KRX code: " + raw);
        }
        return digits;
    }

    private double signedChange(double value, String signCode) {
        if (!StringUtils.hasText(signCode)) {
            return value;
        }
        String normalized = signCode.trim();
        if ("5".equals(normalized) || "6".equals(normalized)) {
            return -value;
        }
        if ("3".equals(normalized)) {
            return 0.0d;
        }
        return value;
    }

    private double parseDouble(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return 0.0d;
        }
    }

    private long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String sanitizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "empty response body";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private void waitForQuoteRateLimit() {
        try {
            Thread.sleep(QUOTE_REQUEST_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KisPortfolioSyncException("Interrupted while waiting for KIS quote rate limit.", exception);
        }
    }
}
