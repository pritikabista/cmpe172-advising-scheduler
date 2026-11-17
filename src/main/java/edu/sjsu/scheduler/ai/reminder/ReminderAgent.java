package edu.sjsu.scheduler.ai.reminder;

import edu.sjsu.scheduler.model.AppointmentRow;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.service.AppointmentNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Agent 2 – autonomous (proactive) agent. Runs on a schedule, not from a user prompt:
 *   1. find BOOKED appointments in the next 48 h that have no reminder yet
 *   2. have Gemini write a personalized reminder (template if Gemini fails)
 *   3. send it through the notification service (same gateway, retry and outbox as Milestone 3)
 *   4. record it, so the same appointment is never reminded twice
 */
@Component
public class ReminderAgent {

    private static final Logger log = LoggerFactory.getLogger(ReminderAgent.class);

    private final AppointmentRepository appointmentRepository;
    private final ReminderMessageGenerator generator;
    private final AppointmentNotifier notifier;
    private final Clock clock;
    private final int windowHours;

    public ReminderAgent(AppointmentRepository appointmentRepository, ReminderMessageGenerator generator,
                         AppointmentNotifier notifier, Clock clock,
                         @Value("${reminders.window-hours:48}") int windowHours) {
        this.appointmentRepository = appointmentRepository;
        this.generator = generator;
        this.notifier = notifier;
        this.clock = clock;
        this.windowHours = windowHours;
    }

    /** Every day at 8:00 by default (reminders.cron). */
    @Scheduled(cron = "${reminders.cron:0 0 8 * * *}")
    public void scheduledRun() {
        run("scheduled");
    }

    /** synchronized: a manual run and the scheduled run can't overlap and double-send. */
    public synchronized ReminderRunResult run(String trigger) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AppointmentRow> due = appointmentRepository.findNeedingReminder(now, now.plusHours(windowHours));
        log.info("Reminder agent ({}): {} appointment(s) need a reminder", trigger, due.size());

        int aiWritten = 0;
        int fallback = 0;
        int delivered = 0;
        int queued = 0;
        for (AppointmentRow appt : due) {
            String message;
            boolean ai;
            try {
                message = generator.generate(appt);
                ai = true;
                aiWritten++;
            } catch (RuntimeException e) {
                // still remind the student, just without AI personalization
                log.warn("Reminder agent: Gemini failed for appointment {} ({}), using template",
                        appt.id(), e.toString());
                message = templateReminder(appt);
                ai = false;
                fallback++;
            }
            boolean sentNow = notifier.appointmentReminder(appt, message);
            if (sentNow) {
                delivered++;
            } else {
                queued++;
            }
            appointmentRepository.recordReminder(appt.id(), shorten(message), ai, sentNow);
            log.info("Reminder agent: appointment {} reminded (ai={}, delivered={})", appt.id(), ai, sentNow);
        }
        ReminderRunResult result = new ReminderRunResult(trigger, due.size(), aiWritten, fallback, delivered, queued);
        log.info("Reminder agent finished: {}", result);
        return result;
    }

    static String templateReminder(AppointmentRow a) {
        String when = a.startTime().format(DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", Locale.US));
        return "Hi " + a.studentName() + ", this is a reminder of your " + a.serviceName()
                + " appointment with " + a.advisorName() + " on " + when + " at " + a.location()
                + ". Please bring your student ID. If you can't come, cancel from My Appointments at least 24 hours ahead.";
    }

    private static String shorten(String s) {
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
