package io.github.huseyinbabal.taskhub.notification;

import io.github.huseyinbabal.taskhub.notification.grpc.NotificationServiceGrpc;
import io.github.huseyinbabal.taskhub.notification.grpc.SubscribeRequest;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEventType;
import io.github.huseyinbabal.taskhub.notification.support.GrpcTestClients;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.grpc.client.ImportGrpcClients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Interceptor behaviour (SPEC §Session 3 acceptance 3): every call is logged, and
 * calls without a valid propagated JWT are rejected with {@code UNAUTHENTICATED} —
 * on the streaming RPC as well as the unary one.
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ExtendWith(OutputCaptureExtension.class)
@ImportGrpcClients(types = NotificationServiceGrpc.NotificationServiceBlockingStub.class)
class GrpcInterceptorTest {

    @Autowired
    private NotificationServiceGrpc.NotificationServiceBlockingStub stub;

    @Autowired
    private TaskEventBroker broker;

    @Test
    void callWithoutATokenIsRejected() {
        assertThatThrownBy(() -> stub.notifyTaskEvent(taskEvent()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void callWithATokenSignedByAnotherSecretIsRejected() {
        var unauthenticated = GrpcTestClients.withAuthorization(stub,
                GrpcTestClients.bearer(GrpcTestClients.foreignToken("mallory")));

        assertThatThrownBy(() -> unauthenticated.notifyTaskEvent(taskEvent()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void callWithAnExpiredTokenIsRejected() {
        var expired = GrpcTestClients.withAuthorization(stub,
                GrpcTestClients.bearer(GrpcTestClients.expiredToken("alice")));

        assertThatThrownBy(() -> expired.notifyTaskEvent(taskEvent()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void streamingSubscriptionWithoutATokenIsRejectedAndRegistersNoSubscriber() {
        assertThatThrownBy(() -> stub.subscribeTaskEvents(SubscribeRequest.newBuilder().build()).next())
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        assertThat(broker.subscriberCount()).isZero();
    }

    @Test
    void authenticatedCallSucceeds() {
        var authenticated = GrpcTestClients.authenticated(stub, "alice");

        assertThat(authenticated.notifyTaskEvent(taskEvent()).getAccepted()).isTrue();
    }

    @Test
    void everyCallIsLoggedWithItsMethodStatusAndCorrelationId(CapturedOutput output) {
        GrpcTestClients.authenticated(stub, "alice").notifyTaskEvent(taskEvent());

        assertThat(output).contains("NotifyTaskEvent").contains("OK").contains("corr-test");
    }

    @Test
    void rejectedCallIsAlsoLogged(CapturedOutput output) {
        assertThatThrownBy(() -> stub.notifyTaskEvent(taskEvent())).isInstanceOf(StatusRuntimeException.class);

        assertThat(output).contains("NotifyTaskEvent").contains("UNAUTHENTICATED");
    }

    private TaskEvent taskEvent() {
        return TaskEvent.newBuilder()
                .setEventId("evt-auth")
                .setType(TaskEventType.TASK_CREATED)
                .setTaskId(1L)
                .setProjectId(1L)
                .setTitle("Ship session 3")
                .setStatus("TODO")
                .setActor("alice")
                .build();
    }
}
