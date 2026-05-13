package com.libra.api.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * libra.auth.jwt.* 매핑.
 * secret 은 최소 256bit (32바이트) 권장.
 */
@ConfigurationProperties(prefix = "libra.auth.jwt")
public record JwtProperties(
    String secret,
    long ttlSeconds,
    String issuer
) {}
