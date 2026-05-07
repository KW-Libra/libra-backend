package com.libra.api.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuthLoginService oauthLoginService;
    private final AuthProperties properties;

    public OAuth2SuccessHandler(OAuthLoginService oauthLoginService, AuthProperties properties) {
        this.oauthLoginService = oauthLoginService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            redirectFailure(request, response, "unexpected_authentication_type");
            return;
        }

        OAuth2User principal = oauthToken.getPrincipal();
        Object infoAttr = principal.getAttributes().get(CustomOAuth2UserService.INFO_KEY);
        if (!(infoAttr instanceof OAuthUserInfo info)) {
            redirectFailure(request, response, "missing_user_info");
            return;
        }

        AuthService.AuthResult result;
        try {
            result = oauthLoginService.upsertAndIssue(
                info,
                request.getHeader("User-Agent"),
                clientIp(request)
            );
        } catch (OAuthLoginService.MissingOAuthEmailException ex) {
            redirectFailure(request, response, "missing_email_from_" + ex.provider());
            return;
        }

        String target = UriComponentsBuilder.fromUriString(properties.oauth().frontendSuccessUri())
            .queryParam("provider", info.provider())
            .queryParam("access_token", result.accessToken().token())
            .queryParam("access_token_expires_at", result.accessToken().expiresAt().toString())
            .queryParam("refresh_token", result.refreshToken().token())
            .queryParam("refresh_token_expires_at", result.refreshToken().expiresAt().toString())
            .build()
            .encode()
            .toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private void redirectFailure(HttpServletRequest request, HttpServletResponse response, String error) throws IOException {
        String target = UriComponentsBuilder.fromUriString(properties.oauth().frontendFailureUri())
            .queryParam("error", error)
            .build()
            .encode()
            .toUriString();
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
