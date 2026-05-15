package com.libra.api.broker.kis.domain;

import com.libra.api.broker.kis.config.KisProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "kis_credentials")
@EntityListeners(AuditingEntityListener.class)
public class KisCredential {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "trading_enabled", nullable = false)
    private boolean tradingEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KisProperties.Environment environment = KisProperties.Environment.PAPER;

    @Column(name = "app_key_encrypted", nullable = false, columnDefinition = "text")
    private String appKeyEncrypted;

    @Column(name = "app_secret_encrypted", nullable = false, columnDefinition = "text")
    private String appSecretEncrypted;

    @Column(name = "account_number", nullable = false, length = 32)
    private String accountNumber;

    @Column(name = "account_product_code", nullable = false, length = 8)
    private String accountProductCode;

    @Column(name = "hts_id_encrypted", columnDefinition = "text")
    private String htsIdEncrypted;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KisCredential() {
        // JPA
    }

    public static KisCredential create(
        UUID userId,
        KisProperties.Environment environment,
        boolean tradingEnabled,
        String appKeyEncrypted,
        String appSecretEncrypted,
        String accountNumber,
        String accountProductCode,
        String htsIdEncrypted
    ) {
        KisCredential credential = new KisCredential();
        credential.userId = userId;
        credential.update(environment, tradingEnabled, appKeyEncrypted, appSecretEncrypted, accountNumber, accountProductCode, htsIdEncrypted);
        return credential;
    }

    public void update(
        KisProperties.Environment environment,
        boolean tradingEnabled,
        String appKeyEncrypted,
        String appSecretEncrypted,
        String accountNumber,
        String accountProductCode,
        String htsIdEncrypted
    ) {
        this.enabled = true;
        this.environment = environment;
        this.tradingEnabled = tradingEnabled;
        this.appKeyEncrypted = appKeyEncrypted;
        this.appSecretEncrypted = appSecretEncrypted;
        this.accountNumber = accountNumber;
        this.accountProductCode = accountProductCode;
        this.htsIdEncrypted = htsIdEncrypted;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
    public boolean isTradingEnabled() { return tradingEnabled; }
    public KisProperties.Environment getEnvironment() { return environment; }
    public String getAppKeyEncrypted() { return appKeyEncrypted; }
    public String getAppSecretEncrypted() { return appSecretEncrypted; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountProductCode() { return accountProductCode; }
    public String getHtsIdEncrypted() { return htsIdEncrypted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
