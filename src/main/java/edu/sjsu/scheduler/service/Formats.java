package edu.sjsu.scheduler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Shared date/time formatting for the pages. */
final class Formats {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    private Formats() {}

    static String date(LocalDateTime t) {
        return t.format(DATE);
    }

    static String timeRange(LocalDateTime start, LocalDateTime end) {
        return start.format(TIME) + " - " + end.format(TIME);
    }
}
