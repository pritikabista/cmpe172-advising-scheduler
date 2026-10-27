package edu.sjsu.scheduler.notification;

/**
 * The DTO sent across the distribution boundary (serialized to JSON).
 * One coarse-grained call carries everything the notification service needs.
 *
 * @param eventId   unique id for this event, e.g. "appointment-42-BOOKED".
 *                  The receiver uses it to ignore duplicates, so retries are idempotent.
 * @param recipient email of the student
 * @param type      BOOKING_CONFIRMED or BOOKING_CANCELLED (later: APPOINTMENT_REMINDER)
 * @param message   full human-readable text, already formatted by the scheduler
 */
public record NotificationRequest(String eventId, String recipient, String type, String message) {

    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
}
