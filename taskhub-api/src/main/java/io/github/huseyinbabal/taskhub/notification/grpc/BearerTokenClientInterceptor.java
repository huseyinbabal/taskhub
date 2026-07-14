package io.github.huseyinbabal.taskhub.notification.grpc;

import io.github.huseyinbabal.taskhub.security.BearerTokenHolder;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Propagates the caller's JWT to notification-service as
 * {@code authorization: Bearer <jwt>} metadata (SPEC §Session 3), which its auth
 * interceptor verifies.
 *
 * <p>When there is no token on the thread the call goes out unauthenticated and the
 * server rejects it with {@code UNAUTHENTICATED} — a deliberate choice: taskhub-api
 * does not hold a service identity that could speak for a user it cannot name.
 */
public class BearerTokenClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public <R, S> ClientCall<R, S> interceptCall(MethodDescriptor<R, S> method, CallOptions callOptions,
                                                 Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<S> responseListener, Metadata headers) {
                String token = BearerTokenHolder.current();
                if (token != null) {
                    headers.put(AUTHORIZATION, BEARER_PREFIX + token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
