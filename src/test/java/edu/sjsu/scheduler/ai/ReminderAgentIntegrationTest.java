package edu.sjsu.scheduler.ai;

import edu.sjsu.scheduler.ai.reminder.ReminderAgent;
import edu.sjsu.scheduler.ai.reminder.ReminderMessageGenerator;
import edu.sjsu.scheduler.ai.reminder.ReminderRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 2 end to end against PostgreSQL, with a fake LLM (no API key needed):
 * finds Alex's appointment tomorrow, sends a reminder (queued, because the notification URL is dead),
 * records it, and does NOT remind the same appointment again on the next run.
 */
@Sql(scripts = {"classpath:schema.sql", "classpath:seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@SpringBootTest(properties = {
        "notification.base-url=http://localhost:1/mock-notify",
        "notification.backoff-ms=10",
        "notification.retry-job.delay-ms=3600000"
})
class ReminderAgentIntegrationTest {

    @TestConfiguration
    static class FakeLlm {
        @Bean
        @Primary
        ReminderMessageGenerator fakeGenerator() {
            return appt -> "Hi " + appt.studentName() + ", see you soon!";
        }
    }

    @Autowired private ReminderAgent agent;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void remindsUpcomingAppointmentsOnce() {
        ReminderRunResult first = agent.run("test");
        assertThat(first.found()).isEqualTo(1);              // seed: Alex, slot 1, tomorrow 9:00
        assertThat(first.aiWritten()).isEqualTo(1);
        assertThat(first.queued()).isEqualTo(1);             // notification service unreachable -> outbox

        String status = jdbc.queryForObject("SELECT status FROM notification_outbox WHERE event_id LIKE '%APPOINTMENT_REMINDER'",
                String.class);
        assertThat(status).isEqualTo("PENDING");

        ReminderRunResult second = agent.run("test");
        assertThat(second.found()).isZero();                 // already reminded
    }
}
