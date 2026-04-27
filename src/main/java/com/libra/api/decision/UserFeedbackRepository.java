package com.libra.api.decision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackRepository extends JpaRepository<UserFeedbackEntity, Long> {

    List<UserFeedbackEntity> findByDecisionRunIdOrderByCreatedAtDesc(String decisionRunId);
}
