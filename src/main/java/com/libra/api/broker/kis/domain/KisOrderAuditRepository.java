package com.libra.api.broker.kis.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KisOrderAuditRepository extends JpaRepository<KisOrderAudit, UUID> {

    Page<KisOrderAudit> findByUserId(UUID userId, Pageable pageable);

    Optional<KisOrderAudit> findByIdAndUserId(UUID id, UUID userId);

    Optional<KisOrderAudit> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
