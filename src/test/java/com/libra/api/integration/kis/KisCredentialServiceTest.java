package com.libra.api.integration.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.libra.api.auth.UserEntity;
import com.libra.api.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "libra.kis.real.base-url=https://openapi.koreainvestment.com:9443",
        "libra.kis.demo.base-url=https://openapivts.koreainvestment.com:29443"
})
@Transactional
class KisCredentialServiceTest {

    @Autowired
    private KisCredentialService service;

    @Autowired
    private KisCredentialRepository repository;

    @Autowired
    private UserRepository userRepository;

    private String userId;

    @BeforeEach
    void setUp() {
        UserEntity user = userRepository.save(UserEntity.newLocalUser(
                "kis-credential@example.com",
                "$2a$10$dummy.hash.for.test.purposes.only0000000000000000000",
                "KIS Credential Test"
        ));
        userId = user.id();
    }

    @Test
    void savesEncryptedCredentialAndReturnsMaskedStatus() {
        KisCredentialStatus status = service.save(userId, new KisCredentialRequest(
                "demo",
                "test-app-key-1234",
                "test-app-secret-5678",
                "12345678-01",
                "1",
                "LIBRA-Test"
        ));

        assertThat(status.configured()).isTrue();
        assertThat(status.environment()).isEqualTo("demo");
        assertThat(status.appKeyMasked()).isEqualTo("test*********1234");
        assertThat(status.accountNoMasked()).isEqualTo("1234*****01");
        assertThat(status.productCode()).isEqualTo("01");

        KisCredentialEntity entity = repository.findById(userId).orElseThrow();
        assertThat(entity.appKeyCiphertext()).doesNotContain("test-app-key-1234");
        assertThat(entity.appSecretCiphertext()).doesNotContain("test-app-secret-5678");
        assertThat(entity.accountNoCiphertext()).doesNotContain("12345678");

        KisProperties.Credential runtime = service.runtimeCredential(userId, null).orElseThrow();
        assertThat(runtime.getAppKey()).isEqualTo("test-app-key-1234");
        assertThat(runtime.getAppSecret()).isEqualTo("test-app-secret-5678");
        assertThat(runtime.getAccountNo()).isEqualTo("12345678-01");
        assertThat(runtime.getProductCode()).isEqualTo("01");
        assertThat(runtime.getUserAgent()).isEqualTo("LIBRA-Test");
    }

    @Test
    void deleteRemovesCredentialStatus() {
        service.save(userId, new KisCredentialRequest(
                "real",
                "app-key",
                "app-secret",
                "12345678",
                "01",
                null
        ));

        service.delete(userId);

        assertThat(service.getStatus(userId).configured()).isFalse();
    }

    @Test
    void storedCredentialEnvironmentWinsOverSyncRequestEnvironment() {
        service.save(userId, new KisCredentialRequest(
                "demo",
                "demo-app-key",
                "demo-app-secret",
                "12345678-01",
                "01",
                null
        ));

        KisProperties.Credential runtime = service.runtimeCredential(userId, "real").orElseThrow();

        assertThat(runtime.getBaseUrl()).contains("openapivts");
        assertThat(runtime.getAppKey()).isEqualTo("demo-app-key");
        assertThat(runtime.getAccountNo()).isEqualTo("12345678-01");
    }
}
