package io.github.huseyinbabal.taskhub.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the gRPC notification service.
 *
 * <p>Session 3 builds out the gRPC server (unary + server-streaming task events)
 * and interceptors. For Session 1 this is a buildable skeleton establishing the
 * module in the reactor.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

}
