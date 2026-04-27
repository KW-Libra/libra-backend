package com.libra.api.decision;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionEvaluationRepository extends JpaRepository<DecisionEvaluationEntity, Long> {

    List<DecisionEvaluationEntity> findByDecisionRunIdOrderByEvaluatedAtDesc(String decisionRunId);

    Optional<DecisionEvaluationEntity> findByDecisionRunIdAndHorizon(String decisionRunId, String horizon);
}
