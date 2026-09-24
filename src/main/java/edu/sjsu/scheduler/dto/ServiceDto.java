package edu.sjsu.scheduler.dto;

public record ServiceDto(Long id, String name, String description, int durationMinutes) {}