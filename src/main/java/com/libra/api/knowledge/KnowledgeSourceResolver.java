package com.libra.api.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libra.api.judge.JudgeRunDispatchRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeSourceResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final KnowledgeProperties properties;
    private final ObjectMapper objectMapper;

    public KnowledgeSourceResolver(KnowledgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, String> resolve(JudgeRunDispatchRequest request) {
        if (!CollectionUtils.isEmpty(request.knowledgeSources())) {
            return request.knowledgeSources();
        }
        if (!StringUtils.hasText(properties.localDir())) {
            return Map.of();
        }

        Path localDir = Path.of(properties.localDir()).toAbsolutePath().normalize();
        Path eventsPath = localDir.resolve("events.json");
        Path normalizedDocumentsPath = localDir.resolve("normalized_documents.json");
        if (!Files.isRegularFile(eventsPath) || !Files.isRegularFile(normalizedDocumentsPath)) {
            return Map.of();
        }
        if (isStale(localDir.resolve("manifest.json"))) {
            return Map.of();
        }

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("events", eventsPath.toString());
        sources.put("normalized_documents", normalizedDocumentsPath.toString());
        Path enrichedDocumentsPath = localDir.resolve("enriched_documents.json");
        if (Files.isRegularFile(enrichedDocumentsPath)) {
            sources.put("enriched_documents", enrichedDocumentsPath.toString());
        }
        return sources;
    }

    private boolean isStale(Path manifestPath) {
        int maxAgeMinutes = properties.maxAgeMinutes();
        if (maxAgeMinutes <= 0) {
            return false;
        }
        if (!Files.isRegularFile(manifestPath)) {
            return true;
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(manifestPath.toFile(), MAP_TYPE);
            Object generatedAt = manifest.get("generated_at");
            if (!StringUtils.hasText(String.valueOf(generatedAt))) {
                return true;
            }
            OffsetDateTime generatedTime = OffsetDateTime.parse(String.valueOf(generatedAt));
            return generatedTime.plus(Duration.ofMinutes(maxAgeMinutes)).isBefore(OffsetDateTime.now(generatedTime.getOffset()));
        } catch (IOException | RuntimeException exception) {
            return true;
        }
    }
}
