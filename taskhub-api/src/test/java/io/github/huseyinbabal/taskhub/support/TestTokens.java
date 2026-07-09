package io.github.huseyinbabal.taskhub.support;

import java.time.Duration;
import java.util.List;

import io.github.huseyinbabal.taskhub.security.JwtService;

/**
 * Builds real signed JWTs for controller slice tests, so requests exercise the
 * actual {@link JwtService} + {@code JwtAuthenticationFilter} path rather than a
 * mocked security context. The secret matches {@code application-dev.yml} (the
 * profile active under {@code @WebMvcTest}).
 */
public final class TestTokens {

    public static final String DEV_SECRET = "dev-only-insecure-secret-change-me-32bytes!!";

    private static final JwtService JWT = new JwtService(DEV_SECRET, Duration.ofHours(1));

    private TestTokens() {
    }

    /** {@code Authorization} header value for a user with the given roles (no ROLE_ prefix). */
    public static String bearer(String username, String... roles) {
        return "Bearer " + JWT.generateToken(username, List.of(roles));
    }
}
