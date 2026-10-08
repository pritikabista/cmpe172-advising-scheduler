package edu.sjsu.scheduler.model;

import java.time.LocalDateTime;

public record AppointmentRow(Long id, String status, String notes,
                             LocalDateTime createdAt, LocalDateTime cancelledAt,
                             LocalDateTime startTime, LocalDateTime endTime,
                             String serviceName, String advisorName, String location,
                             String studentName, String studentEmail) {}
