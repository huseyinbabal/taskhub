package io.github.huseyinbabal.taskhub.notification.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Metadata;

/**
 * The metadata keys and call-scoped context this service exchanges with its gRPC
 * callers (SPEC §Session 3): a propagated bearer token and a correlation id that
 * ties a gRPC call back to the HTTP request that caused it.
 */
public final class GrpcMetadata {

    /** Bearer token propagated by taskhub-api, verified by {@link GrpcAuthInterceptor}. */
    public static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    /** Correlation id propagated by the client so one request can be traced across services. */
    public static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    /** Username of the authenticated caller, set once the token has been verified. */
    public static final Context.Key<String> CALLER = Context.key("taskhub.caller");

    private GrpcMetadata() {
    }

    /** The authenticated caller of the current gRPC call, or {@code "anonymous"} outside one. */
    public static String caller() {
        String caller = CALLER.get();
        return (caller != null) ? caller : "anonymous";
    }
}
