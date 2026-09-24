package edu.sjsu.scheduler.dto;

public record SlotDto(Long id, String date, String time, String serviceName,
                      String advisorName, String location) {}