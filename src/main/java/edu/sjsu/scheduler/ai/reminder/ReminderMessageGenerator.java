package edu.sjsu.scheduler.ai.reminder;

import edu.sjsu.scheduler.model.AppointmentRow;

/** Writes the reminder text for one appointment. The real one uses Gemini; tests use a fake. */
public interface ReminderMessageGenerator {

    String generate(AppointmentRow appointment);
}
