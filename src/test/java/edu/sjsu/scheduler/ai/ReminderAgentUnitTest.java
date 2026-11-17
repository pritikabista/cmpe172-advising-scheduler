package edu.sjsu.scheduler.ai;

import edu.sjsu.scheduler.ai.reminder.ReminderAgent;
import edu.sjsu.scheduler.ai.reminder.ReminderMessageGenerator;
import edu.sjsu.scheduler.ai.reminder.ReminderRunResult;
import edu.sjsu.scheduler.model.AppointmentRow;
import edu.sjsu.scheduler.repository.AppointmentRepository;
import edu.sjsu.scheduler.service.AppointmentNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderAgentUnitTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 8, 0);

    private AppointmentRepository repo;
    private AppointmentNotifier notifier;
    private ReminderMessageGenerator generator;
    private ReminderAgent agent;

    private final AppointmentRow appt = new AppointmentRow(7L, "BOOKED", "CS electives", NOW.minusDays(2), null,
            NOW.plusDays(1), NOW.plusDays(1).plusMinutes(30), "Registration Help", "Dr. Sarah Kim",
            "MacQuarrie Hall 215", "Alex Nguyen", "alex.nguyen@sjsu.edu");

    @BeforeEach
    void setUp() {
        repo = mock(AppointmentRepository.class);
        notifier = mock(AppointmentNotifier.class);
        generator = mock(ReminderMessageGenerator.class);
        agent = new ReminderAgent(repo, generator, notifier, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), 48);
        when(repo.findNeedingReminder(NOW, NOW.plusHours(48))).thenReturn(List.of(appt));
    }

    @Test
    void sendsTheLlmMessageAndRecordsIt() {
        when(generator.generate(appt)).thenReturn("Hi Alex, see you tomorrow!");
        when(notifier.appointmentReminder(appt, "Hi Alex, see you tomorrow!")).thenReturn(true);

        ReminderRunResult r = agent.run("test");

        assertThat(r.aiWritten()).isEqualTo(1);
        assertThat(r.delivered()).isEqualTo(1);
        verify(repo).recordReminder(7L, "Hi Alex, see you tomorrow!", true, true);
    }

    @Test
    void llmFails_templateIsSentInstead() {
        when(generator.generate(any())).thenThrow(new RuntimeException("quota exceeded"));

        ReminderRunResult r = agent.run("test");

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(notifier).appointmentReminder(eq(appt), msg.capture());
        assertThat(msg.getValue()).contains("Alex Nguyen", "Dr. Sarah Kim", "MacQuarrie Hall 215");
        assertThat(r.fallbackUsed()).isEqualTo(1);
        verify(repo).recordReminder(eq(7L), anyString(), eq(false), anyBoolean());
    }
}
