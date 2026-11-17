package edu.sjsu.scheduler.service;

import edu.sjsu.scheduler.model.AppointmentRow;
import edu.sjsu.scheduler.notification.NotificationDeliveryException;
import edu.sjsu.scheduler.notification.NotificationGateway;
import edu.sjsu.scheduler.notification.NotificationOutboxRepository;
import edu.sjsu.scheduler.notification.NotificationRequest;
import edu.sjsu.scheduler.observability.BookingMetrics;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends the book/cancel confirmation. Called by BookingService only AFTER the database
 * change has been committed (commit-then-notify).
 *
 * This class never throws: the booking is already saved, so a notification problem
 * must not turn it into an error for the student. Fallback = save to the outbox for a later retry.
 */
@Component
public class AppointmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotifier.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationGateway gateway;
    private final NotificationOutboxRepository outbox;
    private final BookingMetrics metrics;

    public AppointmentNotifier(AppointmentRepository appointmentRepository, NotificationGateway gateway,
                               NotificationOutboxRepository outbox, BookingMetrics metrics) {
        this.appointmentRepository = appointmentRepository;
        this.gateway = gateway;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    public void appointmentBooked(long appointmentId) {
        notify(appointmentId, NotificationRequest.BOOKING_CONFIRMED);
    }

    public void appointmentCancelled(long appointmentId) {
        notify(appointmentId, NotificationRequest.BOOKING_CANCELLED);
    }

    /**
     * Milestone 4, Agent 2: send an AI-written reminder through the same gateway (same timeout,
     * retry, outbox fallback). Returns true if delivered now, false if queued for retry.
     */
    public boolean appointmentReminder(AppointmentRow appt, String message) {
        String text = message.length() <= 480 ? message : message.substring(0, 480);
        NotificationRequest request = new NotificationRequest(
                "appointment-" + appt.id() + "-" + NotificationRequest.APPOINTMENT_REMINDER,
                appt.studentEmail(), NotificationRequest.APPOINTMENT_REMINDER, text);
        return deliver(request);
    }

    private void notify(long appointmentId, String type) {
        try {
            AppointmentRow appt = appointmentRepository.findById(appointmentId).orElse(null);
            if (appt == null) {
                log.warn("No notification sent: appointment {} not found", appointmentId);
                return;
            }
            deliver(buildRequest(appt, type));
        } catch (RuntimeException e) {
            // Don't fail the (already committed) booking.
            log.error("Could not send or queue notification for appointment {}", appointmentId, e);
        }
    }

    /** Send; on failure fall back to the outbox. Never throws. True = delivered now. */
    private boolean deliver(NotificationRequest request) {
        try {
            try {
                gateway.send(request);
                metrics.notificationSent();
                return true;
            } catch (NotificationDeliveryException e) {
                // fallback: keep the booking, queue the notification for the retry job
                outbox.savePending(request, e.getMessage());
                metrics.notificationFailed();
                log.warn("Notification {} queued for retry: {}", request.eventId(), e.getMessage());
                return false;
            }
        } catch (RuntimeException e) {
            // e.g. the outbox insert failed too
            log.error("Could not send or queue notification {}", request.eventId(), e);
            return false;
        }
    }

    /** Same appointment + same type always gives the same eventId, so retries are idempotent. */
    static NotificationRequest buildRequest(AppointmentRow a, String type) {
        String eventId = "appointment-" + a.id() + "-" + type;
        String when = Formats.date(a.startTime()) + ", " + Formats.timeRange(a.startTime(), a.endTime());
        String message = NotificationRequest.BOOKING_CONFIRMED.equals(type)
                ? "Hi " + a.studentName() + ", your " + a.serviceName() + " appointment with "
                  + a.advisorName() + " on " + when + " at " + a.location() + " is confirmed."
                : "Hi " + a.studentName() + ", your " + a.serviceName() + " appointment with "
                  + a.advisorName() + " on " + when + " has been cancelled.";
        return new NotificationRequest(eventId, a.studentEmail(), type, message);
    }
}
