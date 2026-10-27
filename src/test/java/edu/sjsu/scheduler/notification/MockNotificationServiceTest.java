package edu.sjsu.scheduler.notification;

import edu.sjsu.scheduler.mocknotify.IncomingNotification;
import edu.sjsu.scheduler.mocknotify.MockMode;
import edu.sjsu.scheduler.mocknotify.MockNotificationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The receiving side ignores repeated eventIds, which is what makes client retries safe. */
class MockNotificationServiceTest {

    @Test
    void sameEventTwice_isStoredOnce() {
        MockNotificationService service = new MockNotificationService(MockMode.UP);
        IncomingNotification n = new IncomingNotification(
                "appointment-1-BOOKING_CONFIRMED", "alex.nguyen@sjsu.edu", "BOOKING_CONFIRMED", "hi");

        MockNotificationService.Result first = service.receive(n);
        MockNotificationService.Result second = service.receive(n);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.notification().notificationId()).isEqualTo(first.notification().notificationId());
        assertThat(service.all()).hasSize(1);
    }
}
