package io.github.huseyinbabal.taskhub.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;

/**
 * Reads a {@code Authorization: Bearer <jwt>} header, validates it, and populates
 * the {@link org.springframework.security.core.context.SecurityContextHolder} so
 * downstream authorization sees an authenticated principal. Stateless — no session
 * is created (SPEC §Session 2). An absent or invalid token leaves the context
 * unauthenticated; the security entry point then answers 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                authenticate(header.substring(BEARER_PREFIX.length()), request);
            }
            filterChain.doFilter(request, response);
        }
        finally {
            // Request threads are pooled: a token left behind would be propagated
            // over gRPC on behalf of the next caller.
            BearerTokenHolder.clear();
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            String username = jwtService.extractUsername(token);
            List<String> roles = jwtService.extractRoles(token);
            var authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // Only a verified token is held for propagation to notification-service.
            BearerTokenHolder.set(token);
        } catch (JwtException | IllegalArgumentException ex) {
            // Tampered / expired / malformed token — stay anonymous; the entry point answers 401.
            SecurityContextHolder.clearContext();
            BearerTokenHolder.clear();
        }
    }
}
