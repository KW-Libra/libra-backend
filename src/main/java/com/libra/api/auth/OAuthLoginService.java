package com.libra.api.auth;

import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OAuthLoginService {

    private final UserRepository users;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public OAuthLoginService(UserRepository users, JwtService jwtService, RefreshTokenService refreshTokens) {
        this.users = users;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    public AuthService.AuthResult upsertAndIssue(OAuthUserInfo info, @Nullable String userAgent, @Nullable String ip) {
        if (info.email() == null || info.email().isBlank()) {
            throw new MissingOAuthEmailException(info.provider());
        }

        UserEntity user = users.findByOauthProviderAndOauthProviderId(info.provider(), info.providerId())
            .orElseGet(() -> users.findByEmail(info.email())
                .map(existing -> {
                    existing.linkOauth(info.provider(), info.providerId());
                    return existing;
                })
                .orElseGet(() -> users.save(
                    UserEntity.newOauthUser(info.email(), info.name(), info.provider(), info.providerId())
                ))
            );

        user.recordLogin();

        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issue(user, userAgent, ip);
        return new AuthService.AuthResult(user, access, refresh);
    }

    public static class MissingOAuthEmailException extends RuntimeException {
        private final String provider;

        public MissingOAuthEmailException(String provider) {
            super("OAuth provider " + provider + " did not return an email address. Grant email scope in the provider console.");
            this.provider = provider;
        }

        public String provider() {
            return provider;
        }
    }
}
