package com.libra.api.auth.api.dto;

import com.libra.api.auth.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String displayName,
    Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getCreatedAt()
        );
    }
}
