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
public class PortfolioDefinitionService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PortfolioDefinitionRepository repository;
    private final ObjectMapper objectMapper;

    public PortfolioDefinitionService(PortfolioDefinitionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PortfolioDefinition save(String userId, PortfolioDefinition definition) {
        requireUser(userId);
        validateWeights(definition);
        repository.save(new PortfolioDefinitionEntity(
                userId,
                writeJson(definition),
                LocalDateTime.now(SEOUL)
        ));
        return definition;
    }

    @Transactional(readOnly = true)
    public Optional<PortfolioDefinition> getCurrent(String userId) {
        requireUser(userId);
        return repository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(entity -> readDefinition(entity.getDefinitionPayload()));
    }

    private static void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required for portfolio definition access");
        }
    }

    private static void validateWeights(PortfolioDefinition definition) {
        double total = definition.targetWeights().stream()
                .mapToDouble(TargetWeight::weight)
                .sum();
        if (total < 0.999d || total > 1.001d) {
            throw new IllegalArgumentException("target_weights must sum to 1.0");
        }
    }

    private String writeJson(PortfolioDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize portfolio definition.", exception);
        }
    }

    private PortfolioDefinition readDefinition(String payload) {
        try {
            return objectMapper.readValue(payload, PortfolioDefinition.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored portfolio definition is not valid JSON.", exception);
        }
    }
}
