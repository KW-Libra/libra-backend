package com.libra.api.decision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSignalRepository extends JpaRepository<AgentSignalEntity, Long> {

    List<AgentSignalEntity> findByDecisionRunIdOrderByTurnNumberAscIdAsc(String decisionRunId);
}
