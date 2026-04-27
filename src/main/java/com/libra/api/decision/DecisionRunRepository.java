package com.libra.api.decision;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRunRepository extends JpaRepository<DecisionRunEntity, String> {

    List<DecisionRunEntity> findTop20ByOrderByCreatedAtDesc();

    Optional<DecisionRunEntity> findFirstByThreadIdOrderByCreatedAtDesc(String threadId);
}
