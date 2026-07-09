package io.github.huseyinbabal.taskhub.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Front-end origins allowed to call the API (SPEC §Session 2: configured per
 * environment, never {@code *} in prod). Supplied via {@code taskhub.cors.allowed-origins}.
 *
 * @param allowedOrigins exact origins permitted to send credentialed CORS requests
 */
@ConfigurationProperties(prefix = "taskhub.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
