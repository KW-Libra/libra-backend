package com.libra.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_to_id", length = 36)
    private String rotatedToId;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;

    protected RefreshTokenEntity() {
    }

    private RefreshTokenEntity(
        String id,
        String userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        String userAgent,
        String ip
    ) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ip = ip;
    }

    public static RefreshTokenEntity issue(
        String userId,
        String tokenHash,
        Instant expiresAt,
        String userAgent,
        String ip
    ) {
        return new RefreshTokenEntity(
            UUID.randomUUID().toString(),
            userId,
            tokenHash,
            Instant.now(),
            expiresAt,
            userAgent,
            ip
        );
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public void rotateTo(String nextId) {
        this.rotatedToId = nextId;
        revoke();
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public String id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
