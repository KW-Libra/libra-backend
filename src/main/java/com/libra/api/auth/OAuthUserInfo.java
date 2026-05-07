package com.libra.api.auth;

public record OAuthUserInfo(
    String provider,
    String providerId,
    String email,
    String name
) {
}
