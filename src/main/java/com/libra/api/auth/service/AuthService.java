package com.libra.api.auth.service;

import com.libra.api.auth.api.dto.AuthResponse;
import com.libra.api.auth.api.dto.LoginRequest;
import com.libra.api.auth.api.dto.SignupRequest;
import com.libra.api.auth.domain.User;
import com.libra.api.auth.domain.UserRepository;
import com.libra.api.auth.security.JwtProperties;
import com.libra.api.auth.security.JwtService;
import com.libra.api.common.error.ApiException;
import com.libra.api.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordService passwords;
    private final JwtService jwt;
    private final JwtProperties jwtProps;

    public AuthService(UserRepository users,
                       PasswordService passwords,
                       JwtService jwt,
                       JwtProperties jwtProps) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        }
        User user = new User(req.email(), passwords.hash(req.password()), req.displayName());
        users.save(user);
        return issueFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
            .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!passwords.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return issueFor(user);
    }

    private AuthResponse issueFor(User user) {
        String token = jwt.issue(user.getId(), user.getEmail());
        return new AuthResponse(
            token, "Bearer", jwtProps.ttlSeconds(),
            user.getId(), user.getEmail(), user.getDisplayName());
    }
}
