package edu.sjsu.scheduler.notification;

import edu.sjsu.scheduler.observability.BookingMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recovery: every minute, try again to deliver notifications that are waiting in the outbox.
 * Safe to repeat because each notification keeps the same eventId (the receiver ignores duplicates).
 */
@Component
public class NotificationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryJob.class);
    static final int BATCH_SIZE = 20;

    private final NotificationOutboxRepository outbox;
    private final NotificationGateway gateway;
    private final BookingMetrics metrics;
    private final int maxRounds;

    public NotificationRetryJob(NotificationOutboxRepository outbox, NotificationGateway gateway,
                                BookingMetrics metrics, MeterRegistry registry,
                                @Value("${notification.retry-job.max-rounds:10}") int maxRounds) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.metrics = metrics;
        this.maxRounds = maxRounds;
        // metric: how many notifications are waiting right now
        Gauge.builder("notifications.pending", outbox, o -> o.countByStatus("PENDING"))
                .description("Notifications waiting in the outbox for retry")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${notification.retry-job.delay-ms:60000}",
               initialDelayString = "${notification.retry-job.delay-ms:60000}")
    public void retryPending() {
        List<PendingNotification> pending = outbox.findPending(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retry job: {} pending notification(s)", pending.size());
        for (PendingNotification p : pending) {
            String eventId = p.request().eventId();
            try {
                gateway.send(p.request());
                outbox.markSent(eventId);
                metrics.notificationSent();
                log.info("Retry job: notification {} delivered after {} failed round(s)", eventId, p.attempts());
            } catch (NotificationDeliveryException e) {
                outbox.recordFailure(eventId, e.getMessage(), maxRounds);
                if (p.attempts() + 1 >= maxRounds) {
                    log.error("Retry job: giving up on notification {} after {} rounds", eventId, maxRounds);
                } else {
                    log.warn("Retry job: notification {} still failing, will try again later", eventId);
                }
            }
        }
    }
}
