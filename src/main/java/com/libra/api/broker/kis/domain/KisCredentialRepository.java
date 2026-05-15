package com.libra.api.broker.kis.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KisCredentialRepository extends JpaRepository<KisCredential, UUID> {

    Optional<KisCredential> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
