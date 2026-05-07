package com.libra.api.portfolio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshotEntity, Long> {

    Optional<PortfolioSnapshotEntity> findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
