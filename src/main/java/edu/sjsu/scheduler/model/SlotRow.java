package edu.sjsu.scheduler.model;

import java.time.LocalDateTime;

public record SlotRow(Long id, LocalDateTime startTime, LocalDateTime endTime,
                      String serviceName, int durationMinutes,
                      String advisorName, String department, String officeLocation) {}