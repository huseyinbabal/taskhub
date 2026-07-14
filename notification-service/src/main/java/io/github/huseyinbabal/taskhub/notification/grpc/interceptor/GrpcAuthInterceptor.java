package io.github.huseyinbabal.taskhub.notification.grpc.interceptor;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticates gRPC calls with the JWT that taskhub-api propagates as
 * {@code authorization: Bearer <jwt>} metadata (SPEC §Session 3: interceptors reject
 * unauthenticated gRPC calls).
 *
 * <p>The token is verified against the same HMAC secret taskhub-api signs with, so a
 * missing, malformed, expired, or foreign-signed token is closed with
 * {@code UNAUTHENTICATED} before the handler ever runs — which also means a rejected
 * {@code SubscribeTaskEvents} never registers a subscriber. The verified username is
 * placed in the gRPC {@link Context} for handlers to read.
 *
 * <p>gRPC's own infrastructure services ({@code grpc.health.*}, {@code grpc.reflection.*})
 * are exempt: probes and tooling must reach them without a user token.
 */
public class GrpcAuthInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcAuthInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String GRPC_INFRASTRUCTURE_PREFIX = "grpc.";

    private final SecretKey key;

    public GrpcAuthInterceptor(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public <R, S> ServerCall.Listener<R> interceptCall(ServerCall<R, S> call, Metadata headers,
                                                       ServerCallHandler<R, S> next) {
        if (isInfrastructureCall(call)) {
            return next.startCall(call, headers);
        }

        String authorization = headers.get(GrpcMetadata.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return reject(call, "missing bearer token");
        }

        String username;
        try {
            username = Jwts.parser()
                    .verifyWith(this.key)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()))
                    .getPayload()
                    .getSubject();
        }
        catch (JwtException | IllegalArgumentException ex) {
            // Tampered, expired, or signed with a different key.
            return reject(call, ex.getClass().getSimpleName());
        }

        Context context = Context.current().withValue(GrpcMetadata.CALLER, username);
        return Contexts.interceptCall(context, call, headers, next);
    }

    private boolean isInfrastructureCall(ServerCall<?, ?> call) {
        return call.getMethodDescriptor().getServiceName().startsWith(GRPC_INFRASTRUCTURE_PREFIX);
    }

    private <R, S> ServerCall.Listener<R> reject(ServerCall<R, S> call, String reason) {
        log.warn("Rejecting unauthenticated gRPC call to {}: {}",
                call.getMethodDescriptor().getFullMethodName(), reason);
        call.close(Status.UNAUTHENTICATED.withDescription("A valid bearer token is required"), new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
