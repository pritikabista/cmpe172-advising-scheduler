package edu.sjsu.scheduler.notification;

import edu.sjsu.scheduler.observability.BookingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRetryJobUnitTest {

    private NotificationOutboxRepository outbox;
    private NotificationGateway gateway;
    private NotificationRetryJob job;

    private final NotificationRequest req = new NotificationRequest(
            "appointment-7-BOOKING_CONFIRMED", "maya.j@sjsu.edu", NotificationRequest.BOOKING_CONFIRMED, "hi");

    @BeforeEach
    void setUp() {
        outbox = mock(NotificationOutboxRepository.class);
        gateway = mock(NotificationGateway.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        job = new NotificationRetryJob(outbox, gateway, new BookingMetrics(registry), registry, 10);
        when(outbox.findPending(anyInt())).thenReturn(List.of(new PendingNotification(req, 1)));
    }

    @Test
    void serviceBackUp_marksSent() {
        job.retryPending();
        verify(gateway).send(req);
        verify(outbox).markSent(req.eventId());
    }

    @Test
    void stillDown_recordsAnotherFailedRound() {
        doThrow(new NotificationDeliveryException("down", null)).when(gateway).send(any());
        job.retryPending();
        verify(outbox, never()).markSent(anyString());
        verify(outbox).recordFailure(eq(req.eventId()), anyString(), eq(10));
    }
}
