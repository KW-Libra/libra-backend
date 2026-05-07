package com.libra.api.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AuthProperties properties;

    public OAuth2FailureHandler(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
        throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }
        String message = exception.getMessage() == null ? "oauth_failed" : exception.getMessage();
        String target = UriComponentsBuilder.fromUriString(properties.oauth().frontendFailureUri())
            .queryParam("error", message)
            .build()
            .encode()
            .toUriString();
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
