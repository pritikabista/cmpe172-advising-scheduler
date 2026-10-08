package edu.sjsu.scheduler.model;

import java.time.LocalDateTime;

/** The slot row we read with SELECT ... FOR UPDATE while booking. */
public record SlotLock(Long id, Long providerId, Long serviceId,
                       LocalDateTime startTime, int version) {}
