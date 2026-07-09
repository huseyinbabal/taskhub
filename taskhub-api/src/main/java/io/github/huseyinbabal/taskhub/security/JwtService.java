package io.github.huseyinbabal.taskhub.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates the stateless JSON Web Tokens used to authenticate API
 * clients (SPEC §Session 2). Tokens carry the username as the subject and the
 * caller's roles as a custom claim; they are signed with an HMAC secret so the
 * server can verify integrity without a session store.
 *
 * <p>Parsing rejects any token that is tampered, expired, or signed with a
 * different key by throwing a {@link io.jsonwebtoken.JwtException}.
 */
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(String secret, Duration expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /** Signs a token for {@code username} carrying {@code roles}, valid until the configured expiry. */
    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Returns the subject (username) of a valid token, or throws if the token is invalid/expired/tampered. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /** Returns the roles claim of a valid token. */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return parse(token).get(ROLES_CLAIM, List.class);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
