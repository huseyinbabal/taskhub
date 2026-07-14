package io.github.huseyinbabal.taskhub.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized gRPC settings. The HMAC secret must match the one taskhub-api signs
 * its JWTs with; it is environment-supplied per environment and never committed
 * (SPEC §Boundaries: secrets never in source).
 *
 * @param jwtSecret HMAC secret used to verify propagated bearer tokens
 */
@ConfigurationProperties(prefix = "taskhub.grpc")
public record GrpcProperties(String jwtSecret) {
}
