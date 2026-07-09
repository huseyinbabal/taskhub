package io.github.huseyinbabal.taskhub.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized JWT settings (SPEC §5: no secrets in source). The signing secret
 * is supplied per environment via config/env (dev default in {@code
 * application-dev.yml}; prod requires {@code TASKHUB_JWT_SECRET}).
 *
 * @param secret     HMAC signing secret (must be at least 32 bytes for HS256)
 * @param expiration how long an issued token stays valid
 */
@ConfigurationProperties(prefix = "taskhub.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
