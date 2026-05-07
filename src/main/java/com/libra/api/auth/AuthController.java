package com.libra.api.auth;

import com.libra.api.auth.AuthDtos.LoginRequest;
import com.libra.api.auth.AuthDtos.LogoutRequest;
import com.libra.api.auth.AuthDtos.RefreshRequest;
import com.libra.api.auth.AuthDtos.SignupRequest;
import com.libra.api.auth.AuthDtos.TokenResponse;
import com.libra.api.auth.AuthDtos.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request) {
        AuthService.AuthResult result = authService.signup(
            body.email(),
            body.password(),
            body.name(),
            request.getHeader("User-Agent"),
            clientIp(request)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AuthService.AuthResult result = authService.login(
            body.email(),
            body.password(),
            request.getHeader("User-Agent"),
            clientIp(request)
        );
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        AuthService.AuthResult result = authService.refresh(
            body.refreshToken(),
            request.getHeader("User-Agent"),
            clientIp(request)
        );
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest body) {
        authService.logout(body == null ? null : body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return users.findById(principal.id())
            .map(UserSummary::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private static TokenResponse toResponse(AuthService.AuthResult result) {
        return new TokenResponse(
            result.accessToken().token(),
            result.accessToken().expiresAt(),
            result.refreshToken().token(),
            result.refreshToken().expiresAt(),
            "Bearer",
            UserSummary.from(result.user())
        );
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
