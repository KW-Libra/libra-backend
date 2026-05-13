package com.libra.api.agent.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "libra.agent")
public record AgentProperties(
    URI baseUrl,
    Duration connectTimeout,
    Duration streamTimeout
) {

    public AgentProperties {
        if (baseUrl == null) {
            baseUrl = URI.create("http://localhost:8000");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (streamTimeout == null) {
            streamTimeout = Duration.ofMinutes(30);
        }
    }

    public URI endpoint(String path) {
        String base = baseUrl.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }
}
