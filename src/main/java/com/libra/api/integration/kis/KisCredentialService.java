package com.libra.api.integration.kis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class KisCredentialService {

    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final KisCredentialRepository repository;
    private final KisProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public KisCredentialService(KisCredentialRepository repository, KisProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public KisCredentialStatus getStatus(String userId) {
        return repository.findById(userId)
                .map(entity -> new KisCredentialStatus(
                        true,
                        entity.environment(),
                        mask(decrypt(entity.appKeyCiphertext()), 4, 4),
                        mask(decrypt(entity.accountNoCiphertext()), 4, 2),
                        entity.productCode(),
                        entity.updatedAt()
                ))
                .orElseGet(KisCredentialStatus::empty);
    }

    @Transactional
    public KisCredentialStatus save(String userId, KisCredentialRequest request) {
        String environment = normalizeEnvironment(request.environment());
        String productCode = normalizeProductCode(request.productCode());
        String appKeyCiphertext = encrypt(request.appKey());
        String appSecretCiphertext = encrypt(request.appSecret());
        String accountNoCiphertext = encrypt(request.accountNo());
        Instant now = Instant.now();

        KisCredentialEntity entity = repository.findById(userId)
                .orElseGet(() -> new KisCredentialEntity(
                        userId,
                        environment,
                        appKeyCiphertext,
                        appSecretCiphertext,
                        accountNoCiphertext,
                        productCode,
                        normalizeBlank(request.userAgent()),
                        now,
                        now
                ));
        entity.update(
                environment,
                appKeyCiphertext,
                appSecretCiphertext,
                accountNoCiphertext,
                productCode,
                normalizeBlank(request.userAgent())
        );
        repository.save(entity);
        return getStatus(userId);
    }

    @Transactional
    public void delete(String userId) {
        repository.deleteById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<KisProperties.Credential> runtimeCredential(String userId, String requestedEnvironment) {
        return repository.findById(userId)
                .map(entity -> toRuntimeCredential(entity, requestedEnvironment));
    }

    private KisProperties.Credential toRuntimeCredential(KisCredentialEntity entity, String requestedEnvironment) {
        String environment = normalizeEnvironment(entity.environment());
        KisProperties.Credential defaults = "demo".equals(environment) ? properties.getDemo() : properties.getReal();
        KisProperties.Credential credential = new KisProperties.Credential();
        credential.setBaseUrl(defaults.getBaseUrl());
        credential.setAppKey(decrypt(entity.appKeyCiphertext()));
        credential.setAppSecret(decrypt(entity.appSecretCiphertext()));
        credential.setAccountNo(decrypt(entity.accountNoCiphertext()));
        credential.setProductCode(entity.productCode());
        credential.setUserAgent(StringUtils.hasText(entity.userAgent()) ? entity.userAgent() : defaults.getUserAgent());
        return credential;
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new KisPortfolioSyncException("Failed to encrypt KIS credential.", exception);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new KisPortfolioSyncException("Failed to decrypt KIS credential.", exception);
        }
    }

    private SecretKeySpec keySpec() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.getCredentialSecret().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String normalizeEnvironment(String environment) {
        return "demo".equalsIgnoreCase(environment) ? "demo" : "real";
    }

    private static String normalizeProductCode(String productCode) {
        String digits = productCode == null ? "" : productCode.replaceAll("\\D", "");
        if (!StringUtils.hasText(digits)) {
            return "01";
        }
        return digits.length() == 1 ? "0" + digits : digits.substring(0, 2);
    }

    private static String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String mask(String value, int prefix, int suffix) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= prefix + suffix) {
            return "*".repeat(value.length());
        }
        return value.substring(0, prefix) + "*".repeat(value.length() - prefix - suffix) + value.substring(value.length() - suffix);
    }
}
