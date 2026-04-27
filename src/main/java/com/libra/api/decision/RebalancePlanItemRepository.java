package com.libra.api.decision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebalancePlanItemRepository extends JpaRepository<RebalancePlanItemEntity, Long> {

    List<RebalancePlanItemEntity> findByDecisionRunIdOrderByTickerAsc(String decisionRunId);
}
