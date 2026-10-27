package edu.sjsu.scheduler;

import edu.sjsu.scheduler.notification.NotificationOutboxRepository;
import edu.sjsu.scheduler.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 3 requirement: a booking still succeeds when the notification stub is down.
 * The notification URL points at a port where nothing is listening, so every call fails
 * with "connection refused" (after the gateway's retries).
 */
// Fresh schema + seed data for this class (test contexts are cached and share one database)
@Sql(scripts = {"classpath:schema.sql", "classpath:seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@SpringBootTest(properties = {
        "notification.base-url=http://localhost:1/mock-notify",
        "notification.backoff-ms=10",
        "notification.retry-job.delay-ms=3600000"   // keep the background job out of the way
})
class NotificationDownIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private NotificationOutboxRepository outbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry registry;

    private int bookedCount(long slotId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Integer.class, slotId);
        return n == null ? 0 : n;
    }

    @Test
    void bookingSucceedsAndNotificationIsQueued_whenNotificationServiceIsDown() {
        long appointmentId = bookingService.bookSlot(2, "student_maya", "notification service is down");

        // the booking is committed
        assertThat(appointmentId).isPositive();
        assertThat(bookedCount(2)).isEqualTo(1);

        // the confirmation went to the outbox (fallback) instead of failing the booking
        assertThat(outbox.findStatus("appointment-" + appointmentId + "-BOOKING_CONFIRMED"))
                .isEqualTo("PENDING");
        assertThat(registry.get("notifications.failed").counter().count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void cancelSucceedsAndNotificationIsQueued_whenNotificationServiceIsDown() {
        long appointmentId = bookingService.bookSlot(6, "student_leo", null);

        bookingService.cancel(appointmentId, "student_leo");

        assertThat(bookedCount(6)).isZero();
        assertThat(outbox.findStatus("appointment-" + appointmentId + "-BOOKING_CANCELLED"))
                .isEqualTo("PENDING");
    }
}
