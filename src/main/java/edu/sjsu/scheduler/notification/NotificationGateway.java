package edu.sjsu.scheduler.notification;

/**
 * Gateway to the external notification service.
 * The rest of the app only knows this interface, so the mock can later be swapped for
 * a real channel (Slack/Discord webhook, email) without changing any service code.
 */
public interface NotificationGateway {

    /** Sends one notification. Throws NotificationDeliveryException if it could not be delivered. */
    NotificationResponse send(NotificationRequest request);

    /** Quick reachability check used by GET /health. */
    boolean isAvailable();
}
