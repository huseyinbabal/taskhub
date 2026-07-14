package io.github.huseyinbabal.taskhub.security;

/**
 * Test-only access to the package-private {@link BearerTokenHolder} mutators, which
 * production code deliberately reserves for {@link JwtAuthenticationFilter}. Lets a
 * test stand in for "a request authenticated as this caller is in flight".
 */
public final class BearerTokens {

    private BearerTokens() {
    }

    public static void set(String token) {
        BearerTokenHolder.set(token);
    }

    public static void clear() {
        BearerTokenHolder.clear();
    }
}
