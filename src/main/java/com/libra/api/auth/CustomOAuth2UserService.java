package com.libra.api.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    public static final String INFO_KEY = "libra_oauth_info";
    public static final String NAME_KEY = "libra_oauth_name";

    private final OAuthUserMapper mapper;

    public CustomOAuth2UserService(OAuthUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User raw = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        OAuthUserInfo info = mapper.extract(registrationId, raw);

        Map<String, Object> attrs = new HashMap<>(raw.getAttributes());
        attrs.put(INFO_KEY, info);
        attrs.put(NAME_KEY, info.providerId());

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            attrs,
            NAME_KEY
        );
    }
}
