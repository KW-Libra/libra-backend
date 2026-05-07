package com.libra.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final String issuer;

    public JwtService(AuthProperties properties) {
        byte[] keyBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("libra.auth.jwt.secret must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessTtlMinutes());
        this.issuer = properties.jwt().issuer() == null ? "libra-backend" : properties.jwt().issuer();
    }

    public IssuedToken issueAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl);
        String token = Jwts.builder()
            .issuer(issuer)
            .subject(user.id())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim(CLAIM_EMAIL, user.email())
            .claim(CLAIM_ROLE, user.role())
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
        return new IssuedToken(token, expiresAt);
    }

    public ParsedAccessToken parseAccessToken(String jwt) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(jwt)
            .getPayload();
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!TYPE_ACCESS.equals(type)) {
            throw new IllegalArgumentException("not an access token");
        }
        return new ParsedAccessToken(
            claims.getSubject(),
            claims.get(CLAIM_EMAIL, String.class),
            claims.get(CLAIM_ROLE, String.class),
            claims.getExpiration().toInstant()
        );
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record ParsedAccessToken(String userId, String email, String role, Instant expiresAt) {
    }
}
