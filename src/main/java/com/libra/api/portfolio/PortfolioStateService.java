package com.libra.api.portfolio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PortfolioStateService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PortfolioSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public PortfolioStateService(PortfolioSnapshotRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PortfolioSnapshot save(String userId, PortfolioSnapshot snapshot) {
        return save(userId, snapshot, "MANUAL");
    }

    @Transactional
    public PortfolioSnapshot save(String userId, PortfolioSnapshot snapshot, String source) {
        requireUser(userId);
        String resolvedSource = StringUtils.hasText(source) ? source : "UNKNOWN";
        LocalDateTime now = LocalDateTime.now(SEOUL);
        LocalDateTime generatedAt = snapshot.generatedAt()
                .atZoneSameInstant(SEOUL)
                .toLocalDateTime();
        repository.save(new PortfolioSnapshotEntity(
                userId,
                resolvedSource,
                generatedAt,
                writeJson(snapshot),
                now
        ));
        return snapshot;
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> getCurrent(String userId) {
        requireUser(userId);
        return repository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(entity -> readSnapshot(entity.getSnapshotPayload()));
    }

    private static void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required for portfolio access");
        }
    }

    private String writeJson(PortfolioSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize portfolio snapshot.", exception);
        }
    }

    private PortfolioSnapshot readSnapshot(String payload) {
        try {
            return objectMapper.readValue(payload, PortfolioSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored portfolio snapshot is not valid JSON.", exception);
        }
    }
}
