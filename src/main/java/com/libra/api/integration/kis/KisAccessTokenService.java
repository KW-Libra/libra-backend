package com.libra.api.integration.kis;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class KisAccessTokenService {

    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final Duration DEFAULT_TTL = Duration.ofHours(6);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public String issueAccessToken(KisProperties.Credential credential) {
        validateCredential(credential);
        String key = cacheKey(credential);
        CachedToken cached = cache.get(key);
        OffsetDateTime now = OffsetDateTime.now();
        if (cached != null && cached.expiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
            return cached.accessToken();
        }
        synchronized (cache) {
            cached = cache.get(key);
            now = OffsetDateTime.now();
            if (cached != null && cached.expiresAt().isAfter(now.plus(EXPIRY_MARGIN))) {
                return cached.accessToken();
            }
            CachedToken issued = requestAccessToken(credential, now);
            cache.put(key, issued);
            return issued.accessToken();
        }
    }

    private CachedToken requestAccessToken(KisProperties.Credential credential, OffsetDateTime issuedAt) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("grant_type", "client_credentials");
        payload.put("appkey", credential.getAppKey());
        payload.put("appsecret", credential.getAppSecret());

        try {
            KisTokenResponse response = buildRestClient(credential).post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(KisTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new KisPortfolioSyncException("KIS token issuance returned an empty access token.");
            }
            return new CachedToken(response.accessToken(), expiresAt(response, issuedAt));
        } catch (RestClientResponseException exception) {
            throw new KisPortfolioSyncException(
                    "Failed to issue KIS access token: " + sanitizeBody(exception.getResponseBodyAsString()),
                    exception
            );
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

    private OffsetDateTime expiresAt(KisTokenResponse response, OffsetDateTime issuedAt) {
        if (StringUtils.hasText(response.accessTokenExpiredAt())) {
            try {
                return OffsetDateTime.parse(response.accessTokenExpiredAt().replace(" ", "T") + "+09:00");
            } catch (RuntimeException ignored) {
                // fall through to expires_in/default TTL
            }
        }
        if (response.expiresIn() != null && response.expiresIn() > 0) {
            return issuedAt.plusSeconds(response.expiresIn());
        }
        return issuedAt.plus(DEFAULT_TTL);
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
    }

    private String cacheKey(KisProperties.Credential credential) {
        return credential.getBaseUrl() + "|" + credential.getAppKey();
    }

    private String sanitizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "empty response body";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private record CachedToken(String accessToken, OffsetDateTime expiresAt) {
    }
}
