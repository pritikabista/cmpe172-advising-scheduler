package edu.sjsu.scheduler.model;

import java.time.LocalDateTime;

/** A slot as seen by the advisor who owns it, including who booked it (if anyone). */
public record ProviderSlotRow(Long id, LocalDateTime startTime, LocalDateTime endTime,
                              String serviceName, String bookedBy) {}
