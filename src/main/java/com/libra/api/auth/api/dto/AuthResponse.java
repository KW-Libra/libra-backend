package com.libra.api.auth.api.dto;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    String email,
    String displayName
) {}
