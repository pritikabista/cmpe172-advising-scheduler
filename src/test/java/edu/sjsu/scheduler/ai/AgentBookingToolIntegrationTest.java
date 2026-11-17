package edu.sjsu.scheduler.ai;

import edu.sjsu.scheduler.ai.agent.AppointmentTools;
import edu.sjsu.scheduler.ai.agent.ToolContextKeys;
import edu.sjsu.scheduler.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 4 required test: the AGENT's book tool goes through the same transactional service
 * as the website, so double-booking is still blocked when the agent books.
 *
 * The tools are called directly (exactly what Spring AI does when Gemini picks a tool),
 * so this test needs no API key and runs in CI.
 * Each test uses its own seed slots (2, 3, 4, 6, 10; slot 1 = Alex's booking) so test order doesn't matter.
 */
@Sql(scripts = {"classpath:schema.sql", "classpath:seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@SpringBootTest(properties = {
        "notification.base-url=http://localhost:1/mock-notify",   // notifications just get queued
        "notification.backoff-ms=10",
        "notification.retry-job.delay-ms=3600000"
})
class AgentBookingToolIntegrationTest {

    @Autowired private AppointmentTools tools;
    @Autowired private BookingService bookingService;
    @Autowired private JdbcTemplate jdbc;

    /** What the controller puts in the ToolContext for one chat turn. */
    private static ToolContext ctx(String username, String conversation, int turn) {
        return new ToolContext(Map.of(
                ToolContextKeys.USERNAME, username,
                ToolContextKeys.CONVERSATION_ID, conversation,
                ToolContextKeys.TURN, turn));
    }

    private int bookedCount(long slotId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId);
        return n == null ? 0 : n;
    }

    @Test
    void agentCannotBookOnTheFirstTurn_onlyAfterTheUserConfirms() {
        String r1 = tools.proposeBooking(2, "agent test", ctx("student_maya", "conv-a", 1));
        assertThat(r1).startsWith("PENDING");

        // model tries to confirm in the SAME turn -> refused, nothing booked
        assertThat(tools.confirmPendingAction(ctx("student_maya", "conv-a", 1))).startsWith("REFUSED");
        assertThat(bookedCount(2)).isZero();

        // user replied "yes" -> next turn -> booked through BookingService
        assertThat(tools.confirmPendingAction(ctx("student_maya", "conv-a", 2))).startsWith("BOOKED");
        assertThat(bookedCount(2)).isEqualTo(1);
    }

    @Test
    void slotTakenBetweenProposeAndConfirm_transactionBlocksIt_andAgentOffersAlternatives() {
        // agent proposes slot 3 to Leo while it is still free
        assertThat(tools.proposeBooking(3, null, ctx("student_leo", "conv-b", 1))).startsWith("PENDING");

        // meanwhile Maya books slot 3 on the website
        bookingService.bookSlot(3, "student_maya", null);

        // Leo says yes -> the tool calls BookingService -> transaction/unique index reject it
        String result = tools.confirmPendingAction(ctx("student_leo", "conv-b", 2));
        assertThat(result).startsWith("FAILED").contains("just booked by someone else");
        assertThat(bookedCount(3)).isEqualTo(1);   // still only Maya's booking
    }

    @Test
    void twoAgentsConfirmTheSameSlotAtTheSameTime_onlyOneBooks() throws Exception {
        long slotId = 4;
        tools.proposeBooking(slotId, null, ctx("student_maya", "conv-c1", 1));
        tools.proposeBooking(slotId, null, ctx("student_leo", "conv-c2", 1));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> maya = () -> { start.await(); return tools.confirmPendingAction(ctx("student_maya", "conv-c1", 2)); };
        Callable<String> leo  = () -> { start.await(); return tools.confirmPendingAction(ctx("student_leo", "conv-c2", 2)); };
        List<Future<String>> results = List.of(pool.submit(maya), pool.submit(leo));
        start.countDown();

        long booked = 0;
        long failed = 0;
        for (Future<String> f : results) {
            String r = f.get(15, TimeUnit.SECONDS);
            if (r.startsWith("BOOKED")) booked++;
            if (r.startsWith("FAILED")) failed++;
        }
        pool.shutdown();

        assertThat(booked).isEqualTo(1);
        assertThat(failed).isEqualTo(1);
        assertThat(bookedCount(slotId)).isEqualTo(1);
    }

    @Test
    void alreadyBookedSlot_isReportedUnavailableWithAlternatives() {
        bookingService.bookSlot(10, "student_maya", null);            // slot 10 = Registration Help
        String result = tools.proposeBooking(10, null, ctx("student_leo", "conv-d", 1));
        assertThat(result).startsWith("UNAVAILABLE").contains("Alternatives").contains("Registration Help");
    }

    @Test
    void agentActingForAnAdvisor_isBlockedByRbac() {
        tools.proposeBooking(6, null, ctx("advisor_kim", "conv-e", 1));
        String result = tools.confirmPendingAction(ctx("advisor_kim", "conv-e", 2));
        assertThat(result).startsWith("FAILED (403)");
        assertThat(bookedCount(6)).isZero();
    }

    @Test
    void agentCancelsOnlyAfterConfirmation_andOnlyOwnAppointments() {
        Long apptId = jdbc.queryForObject(
                "SELECT id FROM appointments WHERE slot_id = 1 AND status = 'BOOKED'", Long.class);

        // Leo can't even propose cancelling Alex's appointment
        assertThat(tools.proposeCancellation(apptId, ctx("student_leo", "conv-f", 1))).startsWith("ERROR");

        assertThat(tools.proposeCancellation(apptId, ctx("student_alex", "conv-g", 1))).startsWith("PENDING");
        assertThat(bookedCount(1)).isEqualTo(1);
        assertThat(tools.confirmPendingAction(ctx("student_alex", "conv-g", 2))).startsWith("CANCELLED");
        assertThat(bookedCount(1)).isZero();
    }

    @Test
    void searchToolUsesTheRealSlotData() {
        String result = tools.findAvailableSlots(null, null, "registration", "patel");
        assertThat(result).contains("Registration Help").contains("Raj Patel");
    }
}
