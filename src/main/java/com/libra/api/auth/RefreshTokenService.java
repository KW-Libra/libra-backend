package com.libra.api.auth;

import jakarta.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 48;

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.ttl = Duration.ofDays(properties.refresh().ttlDays());
    }

    public IssuedRefreshToken issue(UserEntity user, @Nullable String userAgent, @Nullable String ip) {
        String raw = randomToken();
        String hash = hash(raw);
        Instant expiresAt = Instant.now().plus(ttl);
        RefreshTokenEntity entity = RefreshTokenEntity.issue(user.id(), hash, expiresAt, userAgent, ip);
        repository.save(entity);
        return new IssuedRefreshToken(entity.id(), raw, expiresAt);
    }

    public RotatedRefreshToken rotate(String rawIncoming, @Nullable String userAgent, @Nullable String ip) {
        String hash = hash(rawIncoming);
        RefreshTokenEntity existing = repository.findByTokenHash(hash)
            .orElseThrow(() -> new InvalidRefreshTokenException("refresh token not found"));
        if (!existing.isUsable()) {
            throw new InvalidRefreshTokenException("refresh token expired or revoked");
        }
        String nextRaw = randomToken();
        String nextHash = hash(nextRaw);
        Instant expiresAt = Instant.now().plus(ttl);
        RefreshTokenEntity next = RefreshTokenEntity.issue(existing.userId(), nextHash, expiresAt, userAgent, ip);
        repository.save(next);
        existing.rotateTo(next.id());
        return new RotatedRefreshToken(existing.userId(), next.id(), nextRaw, expiresAt);
    }

    public void revoke(String rawIncoming) {
        String hash = hash(rawIncoming);
        repository.findByTokenHash(hash).ifPresent(RefreshTokenEntity::revoke);
    }

    public int revokeAllForUser(String userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    private static String randomToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Duration ttl() {
        return ttl;
    }

    public record IssuedRefreshToken(String id, String token, Instant expiresAt) {
    }

    public record RotatedRefreshToken(String userId, String id, String token, Instant expiresAt) {
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
