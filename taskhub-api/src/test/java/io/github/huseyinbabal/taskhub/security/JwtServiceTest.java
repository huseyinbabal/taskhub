package io.github.huseyinbabal.taskhub.security;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the JWT primitive (SPEC §Session 2). Pure logic — no Spring
 * context, no DB. A token must round-trip its subject and roles, and any
 * tampering, expiry, or foreign-key signature must be rejected on parse.
 */
class JwtServiceTest {

    // 256-bit secrets (HMAC-SHA256 minimum) for the service under test.
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";
    private static final String OTHER_SECRET = "a-different-secret-also-32-bytes-long-value!";

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(1));

    @Test
    void generateToken_producesThreePartCompactJws() {
        String token = jwtService.generateToken("alice", List.of("USER"));

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    void extractUsername_returnsSubjectFromOwnToken() {
        String token = jwtService.generateToken("alice", List.of("USER"));

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void extractRoles_returnsRolesClaim() {
        String token = jwtService.generateToken("admin", List.of("USER", "ADMIN"));

        assertThat(jwtService.extractRoles(token)).containsExactly("USER", "ADMIN");
    }

    @Test
    void extractUsername_rejectsTamperedToken() {
        String token = jwtService.generateToken("alice", List.of("USER"));
        // Flip the last character of the signature to simulate tampering.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void extractUsername_rejectsTokenSignedWithAnotherKey() {
        JwtService foreign = new JwtService(OTHER_SECRET, Duration.ofHours(1));
        String foreignToken = foreign.generateToken("mallory", List.of("ADMIN"));

        assertThatThrownBy(() -> jwtService.extractUsername(foreignToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void extractUsername_rejectsExpiredToken() {
        JwtService shortLived = new JwtService(SECRET, Duration.ofSeconds(-1)); // already expired
        String token = shortLived.generateToken("alice", List.of("USER"));

        assertThatThrownBy(() -> jwtService.extractUsername(token))
                .isInstanceOf(RuntimeException.class);
    }
}
