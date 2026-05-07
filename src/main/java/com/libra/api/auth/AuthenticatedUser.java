package com.libra.api.auth;

public record AuthenticatedUser(String id, String email, String role) {
}
