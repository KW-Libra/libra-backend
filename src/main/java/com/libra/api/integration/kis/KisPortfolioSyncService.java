package com.libra.api.integration.kis;

import com.libra.api.portfolio.KisSyncRequest;
import com.libra.api.portfolio.PortfolioSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class KisPortfolioSyncService {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String BALANCE_PATH = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final int MAX_PAGES = 10;

    private final KisProperties properties;
    private final KisPortfolioMapper mapper;
    private final KisAccessTokenService accessTokenService;

    public KisPortfolioSyncService(
            KisProperties properties,
            KisPortfolioMapper mapper,
            KisAccessTokenService accessTokenService
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.accessTokenService = accessTokenService;
    }

    public PortfolioSnapshot syncDomesticPortfolio(KisSyncRequest request) {
        KisProperties.Credential credential = selectCredential(request.environment());
        return syncDomesticPortfolio(request, credential);
    }

    public PortfolioSnapshot syncDomesticPortfolio(KisSyncRequest request, KisProperties.Credential credential) {
        validateCredential(credential, request.environment());

        String accountDigits = digitsOnly(resolveAccountNo(request.accountNo(), credential.getAccountNo()));
        if (accountDigits.length() < 8) {
            throw new KisPortfolioSyncException("KIS account number must include at least 8 digits.");
        }

        String cano = accountDigits.substring(0, 8);
        String productCode = resolveProductCode(request.productCode(), accountDigits, credential.getProductCode());
        RestClient restClient = buildRestClient(credential);
        String accessToken = accessTokenService.issueAccessToken(credential);

        List<KisBalanceHoldingRow> holdings = new ArrayList<>();
        List<KisBalanceSummaryRow> summary = List.of();
        String ctxAreaFk100 = "";
        String ctxAreaNk100 = "";
        String trCont = "";

        for (int page = 0; page < MAX_PAGES; page++) {
            ResponseEntity<KisBalanceResponse> entity = fetchBalancePage(
                    restClient,
                    credential,
                    accessToken,
                    cano,
                    productCode,
                    ctxAreaFk100,
                    ctxAreaNk100,
                    trCont
            );
            KisBalanceResponse body = entity.getBody();
            if (body == null) {
                throw new KisPortfolioSyncException("KIS balance response body was empty.");
            }
            if (!"0".equals(body.rtCd())) {
                throw new KisPortfolioSyncException(
                        "KIS balance inquiry failed: " + body.msgCd() + " " + body.msg1()
                );
            }

            if (body.output1() != null) {
                holdings.addAll(body.output1());
            }
            if (body.output2() != null && !body.output2().isEmpty()) {
                summary = body.output2();
            }

            String nextTrCont = entity.getHeaders().getFirst("tr_cont");
            if (!hasMorePages(nextTrCont)) {
                return mapper.toSnapshot(holdings, summary, request.userPreferences());
            }

            ctxAreaFk100 = nullToEmpty(body.ctxAreaFk100());
            ctxAreaNk100 = nullToEmpty(body.ctxAreaNk100());
            trCont = "N";
        }

        throw new KisPortfolioSyncException("KIS balance inquiry exceeded pagination limit.");
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
        if (!StringUtils.hasText(credential.getAccountNo())) {
            throw new KisPortfolioSyncException("KIS " + envName + " account number is missing.");
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

    private ResponseEntity<KisBalanceResponse> fetchBalancePage(
            RestClient restClient,
            KisProperties.Credential credential,
            String accessToken,
            String cano,
            String productCode,
            String ctxAreaFk100,
            String ctxAreaNk100,
            String trCont
    ) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BALANCE_PATH)
                            .queryParam("CANO", cano)
                            .queryParam("ACNT_PRDT_CD", productCode)
                            .queryParam("AFHR_FLPR_YN", "N")
                            .queryParam("OFL_YN", "")
                            .queryParam("INQR_DVSN", "02")
                            .queryParam("UNPR_DVSN", "01")
                            .queryParam("FUND_STTL_ICLD_YN", "N")
                            .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                            .queryParam("PRCS_DVSN", "01")
                            .queryParam("CTX_AREA_FK100", ctxAreaFk100)
                            .queryParam("CTX_AREA_NK100", ctxAreaNk100)
                            .build())
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("appkey", credential.getAppKey());
                        headers.set("appsecret", credential.getAppSecret());
                        headers.set("tr_id", resolveTrId(credential.getBaseUrl()));
                        headers.set("custtype", "P");
                        headers.set("tr_cont", nullToEmpty(trCont));
                    })
                    .retrieve()
                    .toEntity(KisBalanceResponse.class);
        } catch (RestClientException exception) {
            throw new KisPortfolioSyncException("Failed to fetch KIS balance page.", exception);
        }
    }

    private String resolveTrId(String baseUrl) {
        return baseUrl != null && baseUrl.contains("openapivts")
                ? "VTTC8434R"
                : "TTTC8434R";
    }

    private boolean hasMorePages(String trCont) {
        if (!StringUtils.hasText(trCont)) {
            return false;
        }
        String normalized = trCont.trim().toUpperCase();
        return "M".equals(normalized) || "F".equals(normalized);
    }

    private String resolveAccountNo(String requestAccountNo, String defaultAccountNo) {
        String raw = StringUtils.hasText(requestAccountNo) ? requestAccountNo : defaultAccountNo;
        if (!StringUtils.hasText(raw)) {
            throw new KisPortfolioSyncException("KIS account number is missing.");
        }
        return raw;
    }

    private String resolveProductCode(String requestProductCode, String accountDigits, String defaultProductCode) {
        if (StringUtils.hasText(requestProductCode)) {
            return normalizeProductCode(requestProductCode);
        }
        if (accountDigits.length() >= 10) {
            return accountDigits.substring(accountDigits.length() - 2);
        }
        if (!StringUtils.hasText(defaultProductCode)) {
            throw new KisPortfolioSyncException("KIS product code is missing.");
        }
        return normalizeProductCode(defaultProductCode);
    }

    private String normalizeProductCode(String raw) {
        String digits = digitsOnly(raw);
        if (!StringUtils.hasText(digits)) {
            throw new KisPortfolioSyncException("KIS product code must contain digits.");
        }
        return digits.length() == 1 ? "0" + digits : digits.substring(0, 2);
    }

    private String digitsOnly(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
