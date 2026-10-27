package edu.sjsu.scheduler.notification;

/** A notification waiting in the outbox for another delivery attempt. */
public record PendingNotification(NotificationRequest request, int attempts) {}
