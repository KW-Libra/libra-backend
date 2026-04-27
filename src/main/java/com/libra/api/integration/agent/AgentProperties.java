package com.libra.api.integration.agent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "libra.agent")
public record AgentProperties(
        @NotBlank
        String baseUrl,
        @Min(1)
        int connectTimeoutMs,
        @Min(1)
        int readTimeoutMs,
        boolean fallbackToStub
) {
}
