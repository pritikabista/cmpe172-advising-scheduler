package edu.sjsu.scheduler;

import edu.sjsu.scheduler.mocknotify.MockMode;
import edu.sjsu.scheduler.mocknotify.MockNotificationService;
import edu.sjsu.scheduler.notification.NotificationGateway;
import edu.sjsu.scheduler.notification.NotificationOutboxRepository;
import edu.sjsu.scheduler.notification.NotificationRequest;
import edu.sjsu.scheduler.notification.NotificationResponse;
import edu.sjsu.scheduler.notification.NotificationRetryJob;
import edu.sjsu.scheduler.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the app on a real port, so the scheduler calls the mock notification service over HTTP
 * exactly like in production. Shows: failure -> fallback -> recovery, timeout, idempotency, /health.
 */
// Fresh schema + seed data for this class (test contexts are cached and share one database)
@Sql(scripts = {"classpath:schema.sql", "classpath:seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "notification.backoff-ms=10",
        "notification.retry-job.delay-ms=3600000",  // we run the retry job by hand
        "mock-notify.slow-delay-ms=1500"            // longer than the 1000 ms read timeout
})
class NotificationRecoveryIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private MockNotificationService mockService;
    @Autowired private NotificationOutboxRepository outbox;
    @Autowired private NotificationRetryJob retryJob;
    @Autowired private NotificationGateway gateway;
    @Autowired private Environment env;

    @BeforeEach
    void reset() {
        mockService.clear();
        mockService.setMode(MockMode.UP);
    }

    @AfterEach
    void backToNormal() {
        mockService.setMode(MockMode.UP);
    }

    @Test
    void serviceUp_confirmationIsDeliveredOverHttp() {
        long id = bookingService.bookSlot(7, "student_alex", null);

        assertThat(mockService.all())
                .anyMatch(n -> n.eventId().equals("appointment-" + id + "-BOOKING_CONFIRMED")
                        && n.recipient().equals("alex.nguyen@sjsu.edu"));
        assertThat(outbox.findStatus("appointment-" + id + "-BOOKING_CONFIRMED")).isNull();
    }

    @Test
    void serviceDown_bookingStillWorks_thenRetryJobRecovers() {
        mockService.setMode(MockMode.DOWN);

        long id = bookingService.bookSlot(3, "student_maya", null);
        String eventId = "appointment-" + id + "-BOOKING_CONFIRMED";
        assertThat(outbox.findStatus(eventId)).isEqualTo("PENDING");
        assertThat(mockService.all()).isEmpty();

        // the service comes back and the retry job runs
        mockService.setMode(MockMode.UP);
        retryJob.retryPending();

        assertThat(outbox.findStatus(eventId)).isEqualTo("SENT");
        assertThat(mockService.all()).hasSize(1);

        // running the job again doesn't send anything twice
        retryJob.retryPending();
        assertThat(mockService.all()).hasSize(1);
    }

    @Test
    void serviceSlow_clientTimesOut_bookingStillWorks() {
        mockService.setMode(MockMode.SLOW);

        long id = bookingService.bookSlot(4, "student_leo", null);

        assertThat(id).isPositive();
        assertThat(outbox.findStatus("appointment-" + id + "-BOOKING_CONFIRMED")).isEqualTo("PENDING");
    }

    @Test
    void sendingTheSameEventTwice_isIdempotent() {
        NotificationRequest req = new NotificationRequest(
                "test-event-1", "leo.m@sjsu.edu", NotificationRequest.BOOKING_CONFIRMED, "hello");

        NotificationResponse first = gateway.send(req);
        NotificationResponse second = gateway.send(req);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(mockService.all()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthEndpoint_reportsDatabaseAndNotificationService() {
        RestClient client = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));

        Map<String, Object> up = client.get().uri("/health").retrieve().body(Map.class);
        assertThat(up.get("status")).isEqualTo("UP");
        Map<String, Object> components = (Map<String, Object>) up.get("components");
        assertThat(((Map<String, Object>) components.get("database")).get("status")).isEqualTo("UP");

        mockService.setMode(MockMode.DOWN);
        Map<String, Object> degraded = client.get().uri("/health").retrieve().body(Map.class);
        assertThat(degraded.get("status")).isEqualTo("DEGRADED");
    }
}
