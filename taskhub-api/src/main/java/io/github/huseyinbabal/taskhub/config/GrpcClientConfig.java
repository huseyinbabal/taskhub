package io.github.huseyinbabal.taskhub.config;

import java.util.List;

import io.github.huseyinbabal.taskhub.notification.grpc.BearerTokenClientInterceptor;
import io.github.huseyinbabal.taskhub.notification.grpc.CorrelationIdClientInterceptor;
import io.github.huseyinbabal.taskhub.notification.grpc.NotificationServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * The gRPC client taskhub-api uses to publish task events (SPEC §Session 3).
 *
 * <p>The {@code notifications} channel is configured in {@code application.yml}
 * ({@code spring.grpc.client.channel.notifications.target}). Both client interceptors
 * are attached to it: the caller's JWT is propagated so notification-service can
 * authenticate the call, and the request's correlation id travels with it so the two
 * services' logs can be stitched together.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcClientConfig {

    static final String CHANNEL_NAME = "notifications";

    @Bean
    NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceStub(GrpcChannelFactory channels) {
        ChannelBuilderOptions options = ChannelBuilderOptions.defaults()
                .withInterceptors(List.of(
                        new CorrelationIdClientInterceptor(),
                        new BearerTokenClientInterceptor()));
        return NotificationServiceGrpc.newBlockingStub(channels.createChannel(CHANNEL_NAME, options));
    }
}
