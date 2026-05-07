package com.libra.api.auth;

import jakarta.annotation.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public AuthService(
        UserRepository users,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokens
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    public AuthResult signup(String email, String rawPassword, String name, @Nullable String userAgent, @Nullable String ip) {
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        String hashed = passwordEncoder.encode(rawPassword);
        UserEntity user = users.save(UserEntity.newLocalUser(email, hashed, name));
        return issueTokens(user, userAgent, ip);
    }

    public AuthResult login(String email, String rawPassword, @Nullable String userAgent, @Nullable String ip) {
        UserEntity user = users.findByEmail(email)
            .orElseThrow(InvalidCredentialsException::new);
        if (user.passwordHash() == null || !passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        user.recordLogin();
        return issueTokens(user, userAgent, ip);
    }

    public AuthResult refresh(String rawRefresh, @Nullable String userAgent, @Nullable String ip) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokens.rotate(rawRefresh, userAgent, ip);
        UserEntity user = users.findById(rotated.userId())
            .orElseThrow(() -> new RefreshTokenService.InvalidRefreshTokenException("user not found for refresh token"));
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        return new AuthResult(user, access, new RefreshTokenService.IssuedRefreshToken(rotated.id(), rotated.token(), rotated.expiresAt()));
    }

    public void logout(@Nullable String rawRefresh) {
        if (rawRefresh != null && !rawRefresh.isBlank()) {
            refreshTokens.revoke(rawRefresh);
        }
    }

    private AuthResult issueTokens(UserEntity user, @Nullable String userAgent, @Nullable String ip) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issue(user, userAgent, ip);
        return new AuthResult(user, access, refresh);
    }

    public record AuthResult(
        UserEntity user,
        JwtService.IssuedToken accessToken,
        RefreshTokenService.IssuedRefreshToken refreshToken
    ) {
    }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException(String email) {
            super("email already registered: " + email);
        }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("invalid email or password");
        }
    }
}
