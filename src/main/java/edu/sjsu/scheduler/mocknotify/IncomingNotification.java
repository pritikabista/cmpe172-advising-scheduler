package edu.sjsu.scheduler.mocknotify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO of the mock notification service.
 * The mock has its own copy of the DTO (it does not import the scheduler's classes) because it
 * stands in for a separate external system: the only thing the two sides share is the JSON shape.
 */
public record IncomingNotification(
        @NotBlank @Size(max = 100) String eventId,
        @NotBlank @Size(max = 100) String recipient,
        @NotBlank @Size(max = 30) String type,
        @NotBlank @Size(max = 500) String message) {}
