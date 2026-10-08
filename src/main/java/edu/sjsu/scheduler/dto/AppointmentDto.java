package edu.sjsu.scheduler.dto;

/** Appointment formatted for display. canCancel is true only for upcoming BOOKED appointments. */
public record AppointmentDto(Long id, String date, String time, String serviceName,
                             String advisorName, String location,
                             String studentName, String studentEmail,
                             String status, String notes, boolean canCancel) {}
