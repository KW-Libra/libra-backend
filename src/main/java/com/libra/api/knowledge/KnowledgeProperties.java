package com.libra.api.knowledge;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "libra.knowledge")
public record KnowledgeProperties(
        String localDir,
        @Min(0)
        int maxAgeMinutes
) {
}
