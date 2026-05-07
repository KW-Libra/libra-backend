package com.libra.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    @Column(name = "oauth_provider", length = 32)
    private String oauthProvider;

    @Column(name = "oauth_provider_id", length = 128)
    private String oauthProviderId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    private UserEntity(
        String id,
        String email,
        String passwordHash,
        String name,
        String role,
        String oauthProvider,
        String oauthProviderId,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.oauthProvider = oauthProvider;
        this.oauthProviderId = oauthProviderId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserEntity newLocalUser(String email, String passwordHash, String name) {
        Instant now = Instant.now();
        return new UserEntity(
            UUID.randomUUID().toString(),
            email,
            passwordHash,
            name,
            "USER",
            null,
            null,
            now,
            now
        );
    }

    public static UserEntity newOauthUser(String email, String name, String provider, String providerId) {
        Instant now = Instant.now();
        return new UserEntity(
            UUID.randomUUID().toString(),
            email,
            null,
            name,
            "USER",
            provider,
            providerId,
            now,
            now
        );
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
        this.updatedAt = this.lastLoginAt;
    }

    public void linkOauth(String provider, String providerId) {
        this.oauthProvider = provider;
        this.oauthProviderId = providerId;
        this.updatedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String name() {
        return name;
    }

    public String role() {
        return role;
    }

    public String oauthProvider() {
        return oauthProvider;
    }

    public String oauthProviderId() {
        return oauthProviderId;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }
}
