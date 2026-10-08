package edu.sjsu.scheduler.dto;

public record ProviderSlotDto(Long id, String date, String time, String serviceName,
                              String bookedBy, boolean upcoming) {}
