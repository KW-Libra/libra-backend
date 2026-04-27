package com.libra.api.decision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionExecutionRepository extends JpaRepository<DecisionExecutionEntity, Long> {

    List<DecisionExecutionEntity> findByDecisionRunIdOrderByCreatedAtDesc(String decisionRunId);
}
