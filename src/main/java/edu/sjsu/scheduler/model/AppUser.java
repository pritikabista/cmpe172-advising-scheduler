package edu.sjsu.scheduler.model;

public record AppUser(Long id, String username, String passwordHash,
                      String fullName, String email, String role) {}
