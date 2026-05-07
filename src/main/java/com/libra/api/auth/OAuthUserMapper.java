package com.libra.api.auth;

import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class OAuthUserMapper {

    public OAuthUserInfo extract(String registrationId, OAuth2User user) {
        Map<String, Object> attrs = user.getAttributes();
        return switch (registrationId) {
            case "google" -> mapGoogle(attrs);
            case "kakao" -> mapKakao(attrs);
            case "naver" -> mapNaver(attrs);
            default -> throw new IllegalStateException("unsupported OAuth provider: " + registrationId);
        };
    }

    private static OAuthUserInfo mapGoogle(Map<String, Object> attrs) {
        return new OAuthUserInfo(
            "google",
            String.valueOf(attrs.get("sub")),
            (String) attrs.get("email"),
            stringOr(attrs.get("name"), "Google User")
        );
    }

    @SuppressWarnings("unchecked")
    private static OAuthUserInfo mapKakao(Map<String, Object> attrs) {
        Map<String, Object> account = (Map<String, Object>) attrs.getOrDefault("kakao_account", Map.of());
        Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
        return new OAuthUserInfo(
            "kakao",
            String.valueOf(attrs.get("id")),
            (String) account.get("email"),
            stringOr(profile.get("nickname"), "Kakao User")
        );
    }

    @SuppressWarnings("unchecked")
    private static OAuthUserInfo mapNaver(Map<String, Object> attrs) {
        Map<String, Object> response = (Map<String, Object>) attrs.get("response");
        if (response == null) {
            throw new IllegalStateException("naver user-info response missing 'response' field");
        }
        return new OAuthUserInfo(
            "naver",
            String.valueOf(response.get("id")),
            (String) response.get("email"),
            stringOr(response.get("name"), "Naver User")
        );
    }

    private static String stringOr(Object value, String fallback) {
        if (value == null) return fallback;
        String stringValue = value.toString().trim();
        return stringValue.isEmpty() ? fallback : stringValue;
    }
}
