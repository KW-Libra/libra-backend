package com.libra.api.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

@Configuration
public class OAuthClientConfig {

    private static final String REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(Environment environment) {
        List<ClientRegistration> registrations = new ArrayList<>();
        addIfPresent(registrations, google(environment));
        addIfPresent(registrations, kakao(environment));
        addIfPresent(registrations, naver(environment));
        if (registrations.isEmpty()) {
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration google(Environment environment) {
        String clientId = environment.getProperty("LIBRA_OAUTH_GOOGLE_CLIENT_ID", "");
        String clientSecret = environment.getProperty("LIBRA_OAUTH_GOOGLE_CLIENT_SECRET", "");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return null;
        }
        return CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("email", "profile")
                .redirectUri(REDIRECT_URI)
                .build();
    }

    private ClientRegistration kakao(Environment environment) {
        String clientId = environment.getProperty("LIBRA_OAUTH_KAKAO_CLIENT_ID", "");
        String clientSecret = environment.getProperty("LIBRA_OAUTH_KAKAO_CLIENT_SECRET", "");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return null;
        }
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }

    private ClientRegistration naver(Environment environment) {
        String clientId = environment.getProperty("LIBRA_OAUTH_NAVER_CLIENT_ID", "");
        String clientSecret = environment.getProperty("LIBRA_OAUTH_NAVER_CLIENT_SECRET", "");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return null;
        }
        return ClientRegistration.withRegistrationId("naver")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("email", "name")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
    }

    private void addIfPresent(List<ClientRegistration> registrations, ClientRegistration registration) {
        if (registration != null) {
            registrations.add(registration);
        }
    }
}
