package com.libra.api.broker.kis.service;

import com.libra.api.auth.domain.User;
import com.libra.api.broker.kis.api.dto.KisCredentialStatusResponse;
import com.libra.api.broker.kis.api.dto.KisCredentialUpsertRequest;
import com.libra.api.broker.kis.config.KisProperties;
import com.libra.api.broker.kis.domain.KisCredential;
import com.libra.api.broker.kis.domain.KisCredentialRepository;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KisCredentialService {

    private final KisProperties properties;
    private final KisCredentialRepository credentials;
    private final KisCredentialEncryptionService encryption;

    public KisCredentialService(
        KisProperties properties,
        KisCredentialRepository credentials,
        KisCredentialEncryptionService encryption
    ) {
        this.properties = properties;
        this.credentials = credentials;
        this.encryption = encryption;
    }

    @Transactional(readOnly = true)
    public KisCredentialStatusResponse status(User user) {
        return credentials.findByUserId(user.getId())
            .map(this::statusFromCredential)
            .orElseGet(this::serverOrNoneStatus);
    }

    @Transactional
    public KisCredentialStatusResponse upsert(User user, KisCredentialUpsertRequest request) {
        KisCredential credential = credentials.findByUserId(user.getId())
            .orElseGet(() -> KisCredential.create(
                user.getId(),
                request.environment(),
                request.tradingEnabled(),
                encryption.encrypt(request.appKey()),
                encryption.encrypt(request.appSecret()),
                request.accountNumber(),
                request.accountProductCode(),
                encryption.encrypt(blankToNull(request.htsId()))
            ));

        credential.update(
            request.environment(),
            request.tradingEnabled(),
            encryption.encrypt(request.appKey()),
            encryption.encrypt(request.appSecret()),
            request.accountNumber(),
            request.accountProductCode(),
            encryption.encrypt(blankToNull(request.htsId()))
        );

        return statusFromCredential(credentials.save(credential));
    }

    @Transactional
    public void delete(User user) {
        credentials.deleteByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public KisConnection resolve(User user) {
        Optional<KisCredential> credential = credentials.findByUserId(user.getId());
        if (credential.isPresent()) {
            return connectionFromCredential(credential.get());
        }
        KisConnection fallback = KisConnection.fromProperties(properties);
        if (fallback.enabled() && fallback.hasRestCredentials()) {
            return fallback;
        }
        throw new ApiException(ErrorCode.KIS_CREDENTIAL_NOT_REGISTERED);
    }

    private KisCredentialStatusResponse statusFromCredential(KisCredential credential) {
        return KisCredentialStatusResponse.from(
            credential,
            encryption.decrypt(credential.getAppKeyEncrypted()),
            encryption.decrypt(credential.getHtsIdEncrypted())
        );
    }

    private KisCredentialStatusResponse serverOrNoneStatus() {
        if (!properties.enabled() || !properties.hasRestCredentials()) {
            return KisCredentialStatusResponse.none();
        }
        return new KisCredentialStatusResponse(
            true,
            "server",
            properties.enabled(),
            properties.tradingEnabled(),
            properties.environment().name().toLowerCase(),
            properties.hasRestCredentials(),
            properties.hasAccount(),
            properties.hasWebSocketCredentials(),
            null,
            maskAccount(properties.accountNumber(), properties.accountProductCode()),
            null
        );
    }

    private KisConnection connectionFromCredential(KisCredential credential) {
        return new KisConnection(
            "user",
            credential.isEnabled(),
            credential.isTradingEnabled(),
            credential.getEnvironment(),
            encryption.decrypt(credential.getAppKeyEncrypted()),
            encryption.decrypt(credential.getAppSecretEncrypted()),
            credential.getAccountNumber(),
            credential.getAccountProductCode(),
            encryption.decrypt(credential.getHtsIdEncrypted()),
            credential.getEnvironment() == KisProperties.Environment.PROD ? properties.prodBaseUrl() : properties.paperBaseUrl(),
            properties.maxOrderQuantity(),
            properties.maxOrderAmount(),
            properties.allowedSymbols()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String maskAccount(String accountNumber, String productCode) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return null;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4) + "-" + productCode;
    }
}
