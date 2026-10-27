package edu.sjsu.scheduler.mocknotify;

import java.time.LocalDateTime;

public record StoredNotification(String notificationId, String eventId, String recipient,
                                 String type, String message, LocalDateTime receivedAt) {}
