package io.github.huseyinbabal.taskhub.notification.grpc;

import java.util.UUID;

import io.grpc.Metadata;
import org.slf4j.MDC;

/**
 * The correlation id that ties an inbound HTTP request to the gRPC calls it causes
 * (SPEC §Session 3: client-side correlation-id propagation).
 *
 * <p>The MDC is the carrier: {@code CorrelationIdFilter} puts the id there at the
 * edge of the request, every log line on that thread picks it up, and
 * {@link CorrelationIdClientInterceptor} copies it onto outbound gRPC metadata so
 * notification-service logs the same id.
 */
public final class CorrelationId {

    /** Metadata key notification-service reads the id from. */
    public static final Metadata.Key<String> METADATA_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    /** HTTP header a caller may supply to reuse an existing id. */
    public static final String HEADER = "X-Correlation-Id";

    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** The current request's id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
