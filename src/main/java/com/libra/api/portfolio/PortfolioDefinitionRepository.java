package com.libra.api.portfolio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioDefinitionRepository extends JpaRepository<PortfolioDefinitionEntity, Long> {

    Optional<PortfolioDefinitionEntity> findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
