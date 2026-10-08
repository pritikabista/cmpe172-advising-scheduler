package edu.sjsu.scheduler.dto;

import java.time.LocalDate;

/** Optional filters for the available-slots page. Any field may be null. */
public record SlotFilter(Long providerId, Long serviceId, LocalDate date) {}
