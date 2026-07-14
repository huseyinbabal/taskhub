package io.github.huseyinbabal.taskhub.security;

/**
 * Holds the raw bearer token of the request being served on this thread, so
 * outbound gRPC calls can propagate the caller's identity to notification-service
 * (SPEC §Session 3) instead of inventing a service identity of their own.
 *
 * <p>Set and cleared by {@link JwtAuthenticationFilter} around each request.
 * Clearing is not optional: request threads are pooled, and a token left behind
 * would be propagated on behalf of the <em>next</em> caller.
 */
public final class BearerTokenHolder {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private BearerTokenHolder() {
    }

    static void set(String token) {
        TOKEN.set(token);
    }

    static void clear() {
        TOKEN.remove();
    }

    /** The current caller's raw JWT, or {@code null} when there is no authenticated request. */
    public static String current() {
        return TOKEN.get();
    }
}
