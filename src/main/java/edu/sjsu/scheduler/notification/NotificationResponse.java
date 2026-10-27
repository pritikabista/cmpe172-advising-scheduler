package edu.sjsu.scheduler.notification;

/** What the notification service sends back. duplicate = true means it had already seen this eventId. */
public record NotificationResponse(String notificationId, String status, boolean duplicate) {}
