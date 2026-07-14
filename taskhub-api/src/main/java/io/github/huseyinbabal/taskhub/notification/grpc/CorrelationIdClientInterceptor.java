package io.github.huseyinbabal.taskhub.notification.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Copies the current request's correlation id onto every outbound gRPC call, so one
 * user action can be traced across taskhub-api and notification-service logs
 * (SPEC §Session 3).
 *
 * <p>If there is no id on the current thread — a scheduled or background caller —
 * a fresh one is generated rather than leaving the call untraceable.
 */
public class CorrelationIdClientInterceptor implements ClientInterceptor {

    @Override
    public <R, S> ClientCall<R, S> interceptCall(MethodDescriptor<R, S> method, CallOptions callOptions,
                                                 Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<S> responseListener, Metadata headers) {
                String correlationId = CorrelationId.current();
                headers.put(CorrelationId.METADATA_KEY,
                        (correlationId != null) ? correlationId : CorrelationId.generate());
                super.start(responseListener, headers);
            }
        };
    }
}
