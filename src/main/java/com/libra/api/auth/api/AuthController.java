package com.libra.api.auth.api;

import com.libra.api.auth.api.dto.AuthResponse;
import com.libra.api.auth.api.dto.LoginRequest;
import com.libra.api.auth.api.dto.SignupRequest;
import com.libra.api.auth.api.dto.UserProfileResponse;
import com.libra.api.auth.domain.User;
import com.libra.api.auth.service.AuthService;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody @Valid SignupRequest req) {
        return ResponseEntity.ok(authService.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return UserProfileResponse.from(user);
    }
}
