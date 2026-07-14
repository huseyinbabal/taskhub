package io.github.huseyinbabal.taskhub.notification;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.huseyinbabal.taskhub.notification.grpc.BearerTokenClientInterceptor;
import io.github.huseyinbabal.taskhub.notification.grpc.CorrelationId;
import io.github.huseyinbabal.taskhub.notification.grpc.CorrelationIdClientInterceptor;
import io.github.huseyinbabal.taskhub.notification.grpc.NotificationServiceGrpc;
import io.github.huseyinbabal.taskhub.notification.grpc.NotifyAck;
import io.github.huseyinbabal.taskhub.notification.grpc.SubscribeRequest;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEventType;
import io.github.huseyinbabal.taskhub.security.BearerTokens;
import io.github.huseyinbabal.taskhub.task.TaskStatus;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises the gRPC client end of the notification flow (SPEC §Session 3 acceptance
 * 1) against a real in-process server: the unary call reaches the server, carries the
 * caller's propagated JWT and correlation id, and a failing server never breaks the
 * task mutation that has already been committed.
 */
class TaskEventPublisherTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final CapturingNotificationService server = new CapturingNotificationService();
    private final MetadataCaptor metadata = new MetadataCaptor();

    private Server grpcServer;
    private ManagedChannel channel;
    private TaskEventPublisher publisher;

    @BeforeEach
    void startServer() throws IOException {
        String name = InProcessServerBuilder.generateName();
        this.grpcServer = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(this.server)
                .intercept(this.metadata)
                .build()
                .start();
        this.channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .intercept(new CorrelationIdClientInterceptor(), new BearerTokenClientInterceptor())
                .build();
        this.publisher = new TaskEventPublisher(NotificationServiceGrpc.newBlockingStub(this.channel));
    }

    @AfterEach
    void stopServer() {
        BearerTokens.clear();
        MDC.clear();
        this.channel.shutdownNow();
        this.grpcServer.shutdownNow();
    }

    @Test
    void publishesTheTaskEventAndItsFields() {
        this.publisher.onTaskChanged(taskChanged(TaskChangedEvent.Type.COMPLETED, 7L));

        TaskEvent published = this.server.received();
        assertThat(published.getType()).isEqualTo(TaskEventType.TASK_COMPLETED);
        assertThat(published.getTaskId()).isEqualTo(7L);
        assertThat(published.getProjectId()).isEqualTo(3L);
        assertThat(published.getTitle()).isEqualTo("Ship session 3");
        assertThat(published.getStatus()).isEqualTo("DONE");
        assertThat(published.getAssigneeId()).isEqualTo(42L);
        assertThat(published.getActor()).isEqualTo("alice");
        assertThat(published.getEventId()).isNotBlank();
        assertThat(published.getOccurredAt().getSeconds()).isPositive();
    }

    @Test
    void anUnassignedTaskIsPublishedWithNoAssignee() {
        TaskChangedEvent event = new TaskChangedEvent(TaskChangedEvent.Type.CREATED, 7L, 3L,
                "Ship session 3", TaskStatus.TODO, null, "alice", Instant.now());

        this.publisher.onTaskChanged(event);

        assertThat(this.server.received().getAssigneeId()).isZero();
    }

    @Test
    void propagatesTheCallersTokenAndCorrelationId() {
        BearerTokens.set("the-callers-jwt");
        MDC.put(CorrelationId.MDC_KEY, "corr-42");

        this.publisher.onTaskChanged(taskChanged(TaskChangedEvent.Type.CREATED, 7L));

        Metadata headers = this.metadata.received();
        assertThat(headers.get(AUTHORIZATION)).isEqualTo("Bearer the-callers-jwt");
        assertThat(headers.get(CorrelationId.METADATA_KEY)).isEqualTo("corr-42");
    }

    @Test
    void generatesACorrelationIdWhenTheThreadHasNone() {
        this.publisher.onTaskChanged(taskChanged(TaskChangedEvent.Type.CREATED, 7L));

        assertThat(this.metadata.received().get(CorrelationId.METADATA_KEY)).isNotBlank();
    }

    @Test
    void aFailingNotificationServiceDoesNotBreakTheMutation() {
        this.server.failWith(Status.UNAVAILABLE);

        assertThatCode(() -> this.publisher.onTaskChanged(taskChanged(TaskChangedEvent.Type.CREATED, 7L)))
                .doesNotThrowAnyException();
    }

    @Test
    void aRejectedCallDoesNotBreakTheMutationEither() {
        this.server.failWith(Status.UNAUTHENTICATED);

        assertThatCode(() -> this.publisher.onTaskChanged(taskChanged(TaskChangedEvent.Type.DELETED, 7L)))
                .doesNotThrowAnyException();
    }

    private TaskChangedEvent taskChanged(TaskChangedEvent.Type type, long taskId) {
        TaskStatus status = (type == TaskChangedEvent.Type.COMPLETED) ? TaskStatus.DONE : TaskStatus.TODO;
        return new TaskChangedEvent(type, taskId, 3L, "Ship session 3", status, 42L, "alice", Instant.now());
    }

    /** A real gRPC service that records what it was sent, and can be told to fail. */
    private static final class CapturingNotificationService
            extends NotificationServiceGrpc.NotificationServiceImplBase {

        private final BlockingQueue<TaskEvent> events = new ArrayBlockingQueue<>(4);

        private volatile Status failure;

        void failWith(Status status) {
            this.failure = status;
        }

        @Override
        public void notifyTaskEvent(TaskEvent request, StreamObserver<NotifyAck> responseObserver) {
            this.events.add(request);
            if (this.failure != null) {
                responseObserver.onError(new StatusRuntimeException(this.failure));
                return;
            }
            responseObserver.onNext(NotifyAck.newBuilder()
                    .setEventId(request.getEventId())
                    .setAccepted(true)
                    .setDeliveredTo(1)
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void subscribeTaskEvents(SubscribeRequest request, StreamObserver<TaskEvent> responseObserver) {
            responseObserver.onCompleted();
        }

        TaskEvent received() {
            return poll(this.events, "the server received an event");
        }
    }

    /** Captures the metadata the client interceptors attached. */
    private static final class MetadataCaptor implements ServerInterceptor {

        private final BlockingQueue<Metadata> headers = new ArrayBlockingQueue<>(4);

        @Override
        public <R, S> ServerCall.Listener<R> interceptCall(ServerCall<R, S> call, Metadata headers,
                                                           ServerCallHandler<R, S> next) {
            this.headers.add(headers);
            return next.startCall(call, headers);
        }

        Metadata received() {
            return poll(this.headers, "the server received call metadata");
        }
    }

    private static <T> T poll(BlockingQueue<T> queue, String description) {
        try {
            T value = queue.poll(5, TimeUnit.SECONDS);
            assertThat(value).as(description).isNotNull();
            return value;
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while awaiting " + description, ex);
        }
    }
}
