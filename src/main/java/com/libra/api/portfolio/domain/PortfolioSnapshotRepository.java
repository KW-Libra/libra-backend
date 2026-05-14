package com.libra.api.portfolio.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    Page<PortfolioSnapshot> findByUserId(UUID userId, Pageable pageable);

    Optional<PortfolioSnapshot> findByIdAndUserId(UUID id, UUID userId);

    Optional<PortfolioSnapshot> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
}
