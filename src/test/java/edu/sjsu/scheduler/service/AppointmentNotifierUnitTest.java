package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.model.AppointmentRow;
import edu.sjsu.scheduler.notification.NotificationDeliveryException;
import edu.sjsu.scheduler.notification.NotificationGateway;
import edu.sjsu.scheduler.notification.NotificationOutboxRepository;
import edu.sjsu.scheduler.notification.NotificationRequest;
import edu.sjsu.scheduler.observability.BookingMetrics;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the notification fallback (no network, no DB). */
class AppointmentNotifierUnitTest {

    private AppointmentRepository apptRepo;
    private NotificationGateway gateway;
    private NotificationOutboxRepository outbox;
    private SimpleMeterRegistry registry;
    private AppointmentNotifier notifier;

    @BeforeEach
    void setUp() {
        apptRepo = mock(AppointmentRepository.class);
        gateway = mock(NotificationGateway.class);
        outbox = mock(NotificationOutboxRepository.class);
        registry = new SimpleMeterRegistry();
        notifier = new AppointmentNotifier(apptRepo, gateway, outbox, new BookingMetrics(registry));

        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 9, 0);
        when(apptRepo.findById(42L)).thenReturn(Optional.of(new AppointmentRow(42L, "BOOKED", null,
                start.minusDays(1), null, start, start.plusMinutes(30), "Registration Help",
                "Dr. Sarah Kim", "MacQuarrie Hall 215", "Alex Nguyen", "alex@sjsu.edu")));
    }

    @Test
    void sendsOneCoarseGrainedDtoWithEverythingNeeded() {
        notifier.appointmentBooked(42L);

        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(gateway).send(sent.capture());
        NotificationRequest req = sent.getValue();
        assertThat(req.eventId()).isEqualTo("appointment-42-BOOKING_CONFIRMED");
        assertThat(req.recipient()).isEqualTo("alex@sjsu.edu");
        assertThat(req.type()).isEqualTo(NotificationRequest.BOOKING_CONFIRMED);
        assertThat(req.message()).contains("Registration Help", "Dr. Sarah Kim", "confirmed");
        verify(outbox, never()).savePending(any(), anyString());
    }

    @Test
    void serviceDown_queuesInOutbox_andDoesNotThrow() {
        doThrow(new NotificationDeliveryException("down", null)).when(gateway).send(any());

        assertThatCode(() -> notifier.appointmentBooked(42L)).doesNotThrowAnyException();

        verify(outbox).savePending(any(NotificationRequest.class), anyString());
        assertThat(registry.get("notifications.failed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void evenIfOutboxFails_bookingCallerGetsNoException() {
        doThrow(new NotificationDeliveryException("down", null)).when(gateway).send(any());
        doThrow(new RuntimeException("db down")).when(outbox).savePending(any(), anyString());

        assertThatCode(() -> notifier.appointmentBooked(42L)).doesNotThrowAnyException();
    }

    @Test
    void cancelUsesADifferentEventId() {
        notifier.appointmentCancelled(42L);

        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(gateway).send(sent.capture());
        assertThat(sent.getValue().eventId()).isEqualTo("appointment-42-BOOKING_CANCELLED");
        assertThat(sent.getValue().message()).contains("cancelled");
    }
}
