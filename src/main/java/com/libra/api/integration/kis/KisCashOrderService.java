package com.libra.api.integration.kis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class KisCashOrderService {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String HASH_PATH = "/uapi/hashkey";
    private static final String ORDER_CASH_PATH = "/uapi/domestic-stock/v1/trading/order-cash";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final KisAccessTokenService accessTokenService;

    public KisCashOrderService(ObjectMapper objectMapper, KisAccessTokenService accessTokenService) {
        this.objectMapper = objectMapper;
        this.accessTokenService = accessTokenService;
    }

    public KisCashOrderResult placeDomesticCashOrder(
            KisCashOrderCommand command,
            KisProperties.Credential credential
    ) {
        validateCredential(credential);
        validateCommand(command);

        String accountDigits = digitsOnly(credential.getAccountNo());
        String cano = accountDigits.substring(0, 8);
        String productCode = resolveProductCode(accountDigits, credential.getProductCode());
        RestClient restClient = buildRestClient(credential);
        String accessToken = accessTokenService.issueAccessToken(credential);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("CANO", cano);
        payload.put("ACNT_PRDT_CD", productCode);
        payload.put("PDNO", command.ticker());
        payload.put("ORD_DVSN", normalizeOrderType(command.orderType()));
        payload.put("ORD_QTY", String.valueOf(command.quantity()));
        payload.put("ORD_UNPR", normalizePrice(command));
        payload.put("CTAC_TLNO", "");
        payload.put("SLL_TYPE", "01");
        payload.put("ALGO_NO", "");

        String hashKey = issueHashKey(restClient, credential, payload);
        KisCashOrderResponse response = submitOrder(restClient, credential, accessToken, hashKey, command, payload);
        String status = "0".equals(response.rtCd()) ? "ACCEPTED" : "REJECTED";
        Map<String, Object> rawPayload = objectMapper.convertValue(response, MAP_TYPE);
        rawPayload.put("request", payload);

        return new KisCashOrderResult(
                command.ticker(),
                command.side().toUpperCase(),
                BigDecimal.valueOf(command.quantity()),
                command.priceKrw() == null ? BigDecimal.ZERO : command.priceKrw(),
                amountKrw(command),
                status,
                response.msg1(),
                outputText(response.output(), "ODNO", "odno"),
                outputText(response.output(), "ORD_TMD", "ord_tmd"),
                rawPayload
        );
    }

    private KisCashOrderResponse submitOrder(
            RestClient restClient,
            KisProperties.Credential credential,
            String accessToken,
            String hashKey,
            KisCashOrderCommand command,
            Map<String, Object> payload
    ) {
        try {
            KisCashOrderResponse response = restClient.post()
                    .uri(ORDER_CASH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("appkey", credential.getAppKey());
                        headers.set("appsecret", credential.getAppSecret());
                        headers.set("tr_id", resolveOrderTrId(command.side(), credential.getBaseUrl()));
                        headers.set("custtype", "P");
                        headers.set("hashkey", hashKey);
                    })
                    .retrieve()
                    .body(KisCashOrderResponse.class);
            if (response == null) {
                throw new KisPortfolioSyncException("KIS order response body was empty.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new KisPortfolioSyncException("Failed to submit KIS cash order.", exception);
        }
    }

    private String issueHashKey(
            RestClient restClient,
            KisProperties.Credential credential,
            Map<String, Object> payload
    ) {
        try {
            KisHashKeyResponse response = restClient.post()
                    .uri(HASH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .headers(headers -> {
                        headers.set("appkey", credential.getAppKey());
                        headers.set("appsecret", credential.getAppSecret());
                    })
                    .retrieve()
                    .body(KisHashKeyResponse.class);
            if (response == null || !StringUtils.hasText(response.hash())) {
                throw new KisPortfolioSyncException("KIS hashkey issuance returned an empty hash.");
            }
            return response.hash();
        } catch (RestClientException exception) {
            throw new KisPortfolioSyncException("Failed to issue KIS hashkey.", exception);
        }
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

    private RestClient buildRestClient(KisProperties.Credential credential) {
        RestClient.Builder builder = RestClient.builder().baseUrl(credential.getBaseUrl());
        if (StringUtils.hasText(credential.getUserAgent())) {
            builder.defaultHeader(HttpHeaders.USER_AGENT, credential.getUserAgent());
        }
        return builder.build();
    }

    private void validateCredential(KisProperties.Credential credential) {
        if (!StringUtils.hasText(credential.getBaseUrl())) {
            throw new KisPortfolioSyncException("KIS base URL is missing.");
        }
        if (!StringUtils.hasText(credential.getAppKey())) {
            throw new KisPortfolioSyncException("KIS app key is missing.");
        }
        if (!StringUtils.hasText(credential.getAppSecret())) {
            throw new KisPortfolioSyncException("KIS app secret is missing.");
        }
        if (digitsOnly(credential.getAccountNo()).length() < 8) {
            throw new KisPortfolioSyncException("KIS account number must include at least 8 digits.");
        }
    }

    private void validateCommand(KisCashOrderCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("order command is required");
        }
        if (!StringUtils.hasText(command.ticker()) || !command.ticker().matches("\\d{6}")) {
            throw new IllegalArgumentException("ticker must be a 6 digit KRX code");
        }
        String side = command.side() == null ? "" : command.side().toUpperCase();
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        if (command.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        String orderType = normalizeOrderType(command.orderType());
        if (!"00".equals(orderType) && !"01".equals(orderType)) {
            throw new IllegalArgumentException("order_type must be 00(limit) or 01(market)");
        }
        BigDecimal price = command.priceKrw() == null ? BigDecimal.ZERO : command.priceKrw();
        if ("00".equals(orderType) && price.signum() <= 0) {
            throw new IllegalArgumentException("limit order price must be greater than zero");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price_krw must not be negative");
        }
    }

    private String resolveOrderTrId(String side, String baseUrl) {
        boolean demo = baseUrl != null && baseUrl.contains("openapivts");
        boolean buy = "BUY".equalsIgnoreCase(side);
        if (buy) {
            return demo ? "VTTC0802U" : "TTTC0802U";
        }
        return demo ? "VTTC0801U" : "TTTC0801U";
    }

    private String normalizeOrderType(String orderType) {
        return StringUtils.hasText(orderType) ? orderType.trim() : "01";
    }

    private String normalizePrice(KisCashOrderCommand command) {
        if ("01".equals(normalizeOrderType(command.orderType()))) {
            return "0";
        }
        BigDecimal price = command.priceKrw() == null ? BigDecimal.ZERO : command.priceKrw();
        return price.stripTrailingZeros().toPlainString();
    }

    private BigDecimal amountKrw(KisCashOrderCommand command) {
        BigDecimal price = command.priceKrw() == null ? BigDecimal.ZERO : command.priceKrw();
        return price.multiply(BigDecimal.valueOf(command.quantity()));
    }

    private String resolveProductCode(String accountDigits, String defaultProductCode) {
        if (accountDigits.length() >= 10) {
            return accountDigits.substring(accountDigits.length() - 2);
        }
        String digits = digitsOnly(defaultProductCode);
        if (!StringUtils.hasText(digits)) {
            return "01";
        }
        return digits.length() == 1 ? "0" + digits : digits.substring(0, 2);
    }

    private String outputText(Map<String, Object> output, String... keys) {
        if (output == null) {
            return null;
        }
        for (String key : keys) {
            Object value = output.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String digitsOnly(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }
}
