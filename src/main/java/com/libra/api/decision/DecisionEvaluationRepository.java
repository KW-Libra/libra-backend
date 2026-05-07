package com.libra.api.decision;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionEvaluationRepository extends JpaRepository<DecisionEvaluationEntity, Long> {

    List<DecisionEvaluationEntity> findByDecisionRunIdOrderByEvaluatedAtDesc(String decisionRunId);

    Optional<DecisionEvaluationEntity> findByDecisionRunIdAndHorizon(String decisionRunId, String horizon);

    @Query(
        "select e from DecisionEvaluationEntity e " +
        "where e.decisionRunId in (" +
        "    select dr.id from DecisionRunEntity dr where dr.userId = :userId" +
        ") " +
        "order by e.evaluatedAt desc"
    )
    List<DecisionEvaluationEntity> findRecentByUserId(@Param("userId") String userId, Pageable pageable);
}
