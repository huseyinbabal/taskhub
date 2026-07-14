package io.github.huseyinbabal.taskhub.notification.grpc.interceptor;

import java.util.UUID;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Logs every gRPC call — inbound method and correlation id on arrival, final status
 * and duration on close (SPEC §Session 3: interceptors log every call).
 *
 * <p>Ordered ahead of {@link GrpcAuthInterceptor} so rejected calls are logged too:
 * an {@code UNAUTHENTICATED} close still passes through this interceptor's call
 * wrapper. The caller's correlation id is adopted when supplied and generated when
 * not, so every call is traceable either way.
 */
public class GrpcLoggingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcLoggingInterceptor.class);

    private static final String MDC_KEY = "correlationId";

    @Override
    public <R, S> ServerCall.Listener<R> interceptCall(ServerCall<R, S> call, Metadata headers,
                                                       ServerCallHandler<R, S> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        String correlationId = correlationId(headers);
        long startedAt = System.nanoTime();

        MDC.put(MDC_KEY, correlationId);
        try {
            log.info("gRPC call {} started [correlationId={}]", method, correlationId);
            ServerCall<R, S> logged = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void close(Status status, Metadata trailers) {
                    long millis = (System.nanoTime() - startedAt) / 1_000_000;
                    log.info("gRPC call {} finished with {} in {}ms [correlationId={}]",
                            method, status.getCode(), millis, correlationId);
                    super.close(status, trailers);
                }
            };
            return next.startCall(logged, headers);
        }
        finally {
            // The call continues on gRPC's threads; this thread's MDC must not leak into them.
            MDC.remove(MDC_KEY);
        }
    }

    private String correlationId(Metadata headers) {
        String propagated = headers.get(GrpcMetadata.CORRELATION_ID);
        return (propagated != null && !propagated.isBlank()) ? propagated : UUID.randomUUID().toString();
    }
}
