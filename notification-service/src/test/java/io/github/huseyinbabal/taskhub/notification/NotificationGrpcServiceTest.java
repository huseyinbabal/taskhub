package io.github.huseyinbabal.taskhub.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.huseyinbabal.taskhub.notification.grpc.NotificationServiceGrpc;
import io.github.huseyinbabal.taskhub.notification.grpc.NotifyAck;
import io.github.huseyinbabal.taskhub.notification.grpc.SubscribeRequest;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEventType;
import io.github.huseyinbabal.taskhub.notification.support.GrpcTestClients;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ImportGrpcClients;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-process gRPC tests for the notification server (SPEC §Session 3 acceptance
 * 1 & 2): a unary event is acknowledged and recorded, and a live subscriber
 * receives events in real time — filtered to the project it subscribed to.
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = {
        NotificationServiceGrpc.NotificationServiceBlockingStub.class,
        NotificationServiceGrpc.NotificationServiceStub.class
})
class NotificationGrpcServiceTest {

    @Autowired
    private NotificationServiceGrpc.NotificationServiceBlockingStub rawBlockingStub;

    @Autowired
    private NotificationServiceGrpc.NotificationServiceStub rawAsyncStub;

    @Autowired
    private TaskEventBroker broker;

    @Autowired
    private TaskEventStore store;

    private final List<Subscription> subscriptions = new ArrayList<>();

    /** Both RPCs are authenticated (see {@link GrpcInterceptorTest}); these stubs carry a valid token. */
    private NotificationServiceGrpc.NotificationServiceBlockingStub blockingStub;

    private NotificationServiceGrpc.NotificationServiceStub asyncStub;

    @BeforeEach
    void authenticateAndAwaitNoLeftoverSubscribers() {
        this.blockingStub = GrpcTestClients.authenticated(this.rawBlockingStub, "alice");
        this.asyncStub = GrpcTestClients.authenticated(this.rawAsyncStub, "alice");
        awaitSubscriberCount(0);
    }

    @AfterEach
    void cancelSubscriptions() {
        subscriptions.forEach(Subscription::cancel);
        subscriptions.clear();
        awaitSubscriberCount(0);
    }

    @Test
    void unaryNotifyIsAcknowledgedAndRecorded() {
        NotifyAck ack = blockingStub.notifyTaskEvent(taskEvent("evt-unary", 1L, TaskEventType.TASK_CREATED));

        assertThat(ack.getEventId()).isEqualTo("evt-unary");
        assertThat(ack.getAccepted()).isTrue();
        assertThat(store.recent()).extracting(TaskEvent::getEventId).contains("evt-unary");
    }

    @Test
    void subscriberReceivesEventsInRealTime() {
        Subscription subscription = subscribe(SubscribeRequest.newBuilder().build());

        NotifyAck ack = blockingStub.notifyTaskEvent(taskEvent("evt-stream", 1L, TaskEventType.TASK_UPDATED));

        assertThat(ack.getDeliveredTo()).isEqualTo(1);
        TaskEvent streamed = subscription.next();
        assertThat(streamed.getEventId()).isEqualTo("evt-stream");
        assertThat(streamed.getType()).isEqualTo(TaskEventType.TASK_UPDATED);
    }

    @Test
    void subscriberScopedToAProjectOnlyReceivesThatProjectsEvents() {
        Subscription subscription = subscribe(SubscribeRequest.newBuilder().setProjectId(42L).build());

        NotifyAck other = blockingStub.notifyTaskEvent(taskEvent("evt-other", 7L, TaskEventType.TASK_CREATED));
        NotifyAck mine = blockingStub.notifyTaskEvent(taskEvent("evt-mine", 42L, TaskEventType.TASK_COMPLETED));

        assertThat(other.getDeliveredTo()).isZero();
        assertThat(mine.getDeliveredTo()).isEqualTo(1);
        assertThat(subscription.next().getEventId()).isEqualTo("evt-mine");
    }

    /** Subscribes and waits until the server has registered the stream, so no event is missed. */
    private Subscription subscribe(SubscribeRequest request) {
        Subscription subscription = new Subscription();
        int expected = broker.subscriberCount() + 1;
        asyncStub.subscribeTaskEvents(request, subscription);
        subscriptions.add(subscription);
        awaitSubscriberCount(expected);
        return subscription;
    }

    private void awaitSubscriberCount(int expected) {
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(broker.subscriberCount()).isEqualTo(expected));
    }

    private TaskEvent taskEvent(String eventId, long projectId, TaskEventType type) {
        return TaskEvent.newBuilder()
                .setEventId(eventId)
                .setType(type)
                .setTaskId(1L)
                .setProjectId(projectId)
                .setTitle("Ship session 3")
                .setStatus("IN_PROGRESS")
                .setActor("alice")
                .build();
    }

    /** Captures streamed events and can cancel the stream so it does not leak into the next test. */
    private static final class Subscription implements ClientResponseObserver<SubscribeRequest, TaskEvent> {

        private final BlockingQueue<TaskEvent> received = new ArrayBlockingQueue<>(8);

        private ClientCallStreamObserver<SubscribeRequest> call;

        @Override
        public void beforeStart(ClientCallStreamObserver<SubscribeRequest> call) {
            this.call = call;
        }

        @Override
        public void onNext(TaskEvent event) {
            this.received.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onCompleted() {
        }

        TaskEvent next() {
            try {
                TaskEvent event = this.received.poll(5, TimeUnit.SECONDS);
                assertThat(event).as("subscriber received an event").isNotNull();
                return event;
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while awaiting a streamed event", ex);
            }
        }

        void cancel() {
            this.call.cancel("test finished", null);
        }
    }
}
