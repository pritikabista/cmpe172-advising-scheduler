package edu.sjsu.scheduler.ai.reminder;

/**
 * Summary of one reminder-agent run.
 * aiWritten = written by Gemini, fallbackUsed = template used because Gemini failed,
 * delivered = sent now, queued = notification service down, saved to the outbox.
 */
public record ReminderRunResult(String trigger, int found, int aiWritten, int fallbackUsed,
                                int delivered, int queued) {}
