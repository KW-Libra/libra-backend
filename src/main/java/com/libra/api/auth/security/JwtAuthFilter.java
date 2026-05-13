package com.libra.api.auth.security;

import com.libra.api.auth.domain.User;
import com.libra.api.auth.domain.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer 토큰을 헤더 또는 쿼리스트링 (?token=...) 에서 추출해 인증.
 *
 * 쿼리스트링 허용 이유: EventSource (SSE) API 는 헤더 못 박음. 보안 트레이드오프:
 *  - 토큰이 액세스 로그/Referer 에 찍힐 수 있음 → JWT TTL 짧게 유지
 *  - 운영 시 nginx 액세스 로그에서 token 파라미터 마스킹 권장
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = extractToken(req);

        if (token != null) {
            try {
                Claims claims = jwtService.parse(token);
                UUID userId = UUID.fromString(claims.getSubject());
                users.findById(userId)
                     .filter(User::isActive)
                     .ifPresent(user -> {
                         var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
                         SecurityContextHolder.getContext().setAuthentication(auth);
                     });
            } catch (JwtException | IllegalArgumentException e) {
                // invalid token → 인증 안 된 상태로 통과. permitAll 경로는 OK, 보호된 경로는 401.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    private String extractToken(HttpServletRequest req) {
        String header = req.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        String q = req.getParameter("token");
        if (q != null && !q.isBlank()) {
            return q;
        }
        return null;
    }
}
