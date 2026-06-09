package com.libra.api.agent.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    Page<AgentRun> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<AgentRun> findByIdAndUserId(UUID id, UUID userId);

    Optional<AgentRun> findFirstByThreadIdAndUserIdOrderByCreatedAtDesc(String threadId, UUID userId);
}
