package edu.sjsu.scheduler.notification;

/** The notification could not be delivered after all retries. */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
