package edu.sjsu.scheduler.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Application metrics (Micrometer). View them at /actuator/metrics/{name}, e.g.
 *   /actuator/metrics/bookings.created
 *   /actuator/metrics/bookings.failed
 *   /actuator/metrics/booking.latency
 *   /actuator/metrics/notifications.failed
 */
@Component
public class BookingMetrics {

    private final MeterRegistry registry;
    private final Counter bookingsCreated;
    private final Counter bookingsCancelled;
    private final Counter notificationsSent;
    private final Counter notificationsFailed;
    private final Timer bookingLatency;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.bookingsCreated = Counter.builder("bookings.created")
                .description("Successful bookings").register(registry);
        this.bookingsCancelled = Counter.builder("bookings.cancelled")
                .description("Cancelled appointments").register(registry);
        this.notificationsSent = Counter.builder("notifications.sent")
                .description("Notifications delivered to the notification service").register(registry);
        this.notificationsFailed = Counter.builder("notifications.failed")
                .description("Notifications that failed and were queued for retry").register(registry);
        this.bookingLatency = Timer.builder("booking.latency")
                .description("Time to validate + run the booking transaction (excludes notification)")
                .register(registry);
    }

    public void bookingCreated() {
        bookingsCreated.increment();
    }

    /** reason = exception name, e.g. SlotAlreadyBookedException, so we can see why bookings fail. */
    public void bookingFailed(String reason) {
        Counter.builder("bookings.failed")
                .description("Booking attempts that ended in an error")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void recordBookingLatency(long nanos) {
        bookingLatency.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void appointmentCancelled() {
        bookingsCancelled.increment();
    }

    public void notificationSent() {
        notificationsSent.increment();
    }

    public void notificationFailed() {
        notificationsFailed.increment();
    }
}
