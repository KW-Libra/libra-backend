package com.libra.api.integration.kis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "kis_credentials")
public class KisCredentialEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "environment", nullable = false, length = 16)
    private String environment;

    @Column(name = "app_key_ciphertext", nullable = false, columnDefinition = "text")
    private String appKeyCiphertext;

    @Column(name = "app_secret_ciphertext", nullable = false, columnDefinition = "text")
    private String appSecretCiphertext;

    @Column(name = "account_no_ciphertext", nullable = false, columnDefinition = "text")
    private String accountNoCiphertext;

    @Column(name = "product_code", nullable = false, length = 8)
    private String productCode;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KisCredentialEntity() {
    }

    public KisCredentialEntity(
            String userId,
            String environment,
            String appKeyCiphertext,
            String appSecretCiphertext,
            String accountNoCiphertext,
            String productCode,
            String userAgent,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.environment = environment;
        this.appKeyCiphertext = appKeyCiphertext;
        this.appSecretCiphertext = appSecretCiphertext;
        this.accountNoCiphertext = accountNoCiphertext;
        this.productCode = productCode;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            String environment,
            String appKeyCiphertext,
            String appSecretCiphertext,
            String accountNoCiphertext,
            String productCode,
            String userAgent
    ) {
        this.environment = environment;
        this.appKeyCiphertext = appKeyCiphertext;
        this.appSecretCiphertext = appSecretCiphertext;
        this.accountNoCiphertext = accountNoCiphertext;
        this.productCode = productCode;
        this.userAgent = userAgent;
        this.updatedAt = Instant.now();
    }

    public String userId() {
        return userId;
    }

    public String environment() {
        return environment;
    }

    public String appKeyCiphertext() {
        return appKeyCiphertext;
    }

    public String appSecretCiphertext() {
        return appSecretCiphertext;
    }

    public String accountNoCiphertext() {
        return accountNoCiphertext;
    }

    public String productCode() {
        return productCode;
    }

    public String userAgent() {
        return userAgent;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
