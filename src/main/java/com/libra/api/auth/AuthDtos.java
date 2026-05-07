package com.libra.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 120) String name
    ) {
    }

    public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
    ) {
    }

    public record RefreshRequest(
        @JsonProperty("refresh_token") @NotBlank String refreshToken
    ) {
    }

    public record LogoutRequest(
        @JsonProperty("refresh_token") String refreshToken
    ) {
    }

    public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("access_token_expires_at") Instant accessTokenExpiresAt,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_token_expires_at") Instant refreshTokenExpiresAt,
        @JsonProperty("token_type") String tokenType,
        UserSummary user
    ) {
    }

    public record UserSummary(
        String id,
        String email,
        String name,
        String role,
        @JsonProperty("oauth_provider") String oauthProvider
    ) {
        public static UserSummary from(UserEntity entity) {
            return new UserSummary(
                entity.id(),
                entity.email(),
                entity.name(),
                entity.role(),
                entity.oauthProvider()
            );
        }
    }
}
