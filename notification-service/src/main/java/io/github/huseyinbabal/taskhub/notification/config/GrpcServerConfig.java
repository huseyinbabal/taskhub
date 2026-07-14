package io.github.huseyinbabal.taskhub.notification.config;

import io.github.huseyinbabal.taskhub.notification.grpc.interceptor.GrpcAuthInterceptor;
import io.github.huseyinbabal.taskhub.notification.grpc.interceptor.GrpcLoggingInterceptor;
import io.grpc.ServerInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.util.Assert;

/**
 * Registers the global server interceptors (SPEC §Session 3). Order matters:
 * logging runs first so that calls the auth interceptor rejects are logged as well,
 * with their {@code UNAUTHENTICATED} status.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GrpcProperties.class)
public class GrpcServerConfig {

    @Bean
    @Order(0)
    @GlobalServerInterceptor
    ServerInterceptor grpcLoggingInterceptor() {
        return new GrpcLoggingInterceptor();
    }

    @Bean
    @Order(10)
    @GlobalServerInterceptor
    ServerInterceptor grpcAuthInterceptor(GrpcProperties properties) {
        Assert.hasText(properties.jwtSecret(),
                "taskhub.grpc.jwt-secret must be set — gRPC calls are authenticated with the token taskhub-api signs");
        return new GrpcAuthInterceptor(properties.jwtSecret());
    }
}
