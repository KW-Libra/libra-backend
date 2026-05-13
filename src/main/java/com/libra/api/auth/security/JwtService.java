package com.libra.api.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] raw = props.secret().getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                "libra.auth.jwt.secret 은 최소 32바이트 (256bit) 이상이어야 합니다. 현재: " + raw.length);
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String issue(UUID userId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.ttlSeconds());
        return Jwts.builder()
            .issuer(props.issuer())
            .subject(userId.toString())
            .claims(Map.of("email", email))
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
