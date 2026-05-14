package com.libra.api.broker.kis.service;

import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KisAuthClient {

    private static final DateTimeFormatter KIS_EXPIRES_AT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KisProperties properties;
    private final RestClient restClient;

    private volatile CachedToken cachedToken;

    public KisAuthClient(KisProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            .build();
    }

    public String accessToken() {
        ensureConfigured();
        CachedToken token = cachedToken;
        if (token != null && token.isUsable()) {
            return token.value();
        }
        synchronized (this) {
            token = cachedToken;
            if (token != null && token.isUsable()) {
                return token.value();
            }
            TokenResponse response = requestAccessToken();
            cachedToken = new CachedToken(response.accessToken(), response.expiresAt());
            return cachedToken.value();
        }
    }

    public String issueApprovalKey() {
        ensureConfigured();
        try {
            ApprovalResponse response = restClient.post()
                .uri("/oauth2/Approval")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "grant_type", "client_credentials",
                    "appkey", properties.appKey(),
                    "secretkey", properties.appSecret()
                ))
                .retrieve()
                .body(ApprovalResponse.class);
            if (response == null || response.approvalKey() == null || response.approvalKey().isBlank()) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS approval key response is empty");
            }
            return response.approvalKey();
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS approval key request failed");
        }
    }

    public String hashKey(Map<String, ?> body) {
        ensureConfigured();
        try {
            HashResponse response = restClient.post()
                .uri("/uapi/hashkey")
                .contentType(MediaType.APPLICATION_JSON)
                .header("appkey", properties.appKey())
                .header("appsecret", properties.appSecret())
                .body(body)
                .retrieve()
                .body(HashResponse.class);
            if (response == null || response.hash() == null || response.hash().isBlank()) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS hashkey response is empty");
            }
            return response.hash();
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS hashkey request failed");
        }
    }

    private TokenResponse requestAccessToken() {
        try {
            RawTokenResponse response = restClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "grant_type", "client_credentials",
                    "appkey", properties.appKey(),
                    "appsecret", properties.appSecret()
                ))
                .retrieve()
                .body(RawTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS access token response is empty");
            }
            return new TokenResponse(response.accessToken(), parseExpiresAt(response.expiresAt()));
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.KIS_UNAVAILABLE, "KIS access token request failed");
        }
    }

    private void ensureConfigured() {
        if (!properties.enabled() || !properties.hasRestCredentials()) {
            throw new ApiException(ErrorCode.KIS_NOT_CONFIGURED);
        }
    }

    private static ZonedDateTime parseExpiresAt(String value) {
        if (value == null || value.isBlank()) {
            return ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(23);
        }
        LocalDateTime local = LocalDateTime.parse(value, KIS_EXPIRES_AT);
        return local.atZone(ZoneId.of("Asia/Seoul"));
    }

    private record CachedToken(String value, ZonedDateTime expiresAt) {
        boolean isUsable() {
            return ZonedDateTime.now(expiresAt.getZone()).plusMinutes(5).isBefore(expiresAt);
        }
    }

    private record TokenResponse(String accessToken, ZonedDateTime expiresAt) {
    }

    private record RawTokenResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
        @com.fasterxml.jackson.annotation.JsonProperty("access_token_token_expired") String expiresAt
    ) {
    }

    private record ApprovalResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("approval_key") String approvalKey
    ) {
    }

    private record HashResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("HASH") String hash
    ) {
    }
}
