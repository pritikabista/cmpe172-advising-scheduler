package edu.sjsu.scheduler.model;

import java.math.BigDecimal;

public record AdvisingService(Long id, String name, String description,
                              int durationMinutes, BigDecimal price) {}