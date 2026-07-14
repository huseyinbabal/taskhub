package io.github.huseyinbabal.taskhub.notification.support;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Signs real JWTs with the dev secret from {@code application-dev.yml} (the profile
 * active in tests) and attaches them — plus a correlation id — as gRPC metadata, so
 * tests exercise the real interceptor path rather than bypassing it.
 */
public final class GrpcTestClients {

    public static final String DEV_SECRET = "dev-only-insecure-secret-change-me-32bytes!!";

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcTestClients() {
    }

    /** Returns {@code stub} carrying a valid token for {@code username} and a correlation id. */
    public static <S extends AbstractStub<S>> S authenticated(S stub, String username) {
        return withMetadata(stub, bearer(token(username, Duration.ofHours(1))), "corr-test");
    }

    /** Returns {@code stub} carrying the given raw {@code authorization} value (may be invalid). */
    public static <S extends AbstractStub<S>> S withAuthorization(S stub, String authorization) {
        return withMetadata(stub, authorization, "corr-test");
    }

    /** Signs a token that is already expired — used to prove verification is real. */
    public static String expiredToken(String username) {
        return token(username, Duration.ofHours(-1));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** A token signed with a different secret — must not be accepted. */
    public static String foreignToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", List.of("USER"))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(key("some-other-secret-that-is-32-bytes-long!!"))
                .compact();
    }

    private static <S extends AbstractStub<S>> S withMetadata(S stub, String authorization, String correlationId) {
        Metadata metadata = new Metadata();
        if (authorization != null) {
            metadata.put(AUTHORIZATION, authorization);
        }
        metadata.put(CORRELATION_ID, correlationId);
        ClientInterceptor interceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
        return stub.withInterceptors(interceptor);
    }

    private static String token(String username, Duration validFor) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(now.minus(Duration.ofMinutes(5))))
                .expiration(Date.from(now.plus(validFor)))
                .signWith(key(DEV_SECRET))
                .compact();
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
